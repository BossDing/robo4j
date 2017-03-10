/*
 * Copyright (c) 2014, 2017, Marcus Hirt, Miroslav Wengner
 * 
 * Robo4J is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Robo4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Robo4J. If not, see <http://www.gnu.org/licenses/>.
 */
package com.robo4j.units.rpi.lidarlite;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.robo4j.core.ConfigurationException;
import com.robo4j.core.RoboContext;
import com.robo4j.core.RoboReference;
import com.robo4j.core.configuration.Configuration;
import com.robo4j.core.logging.SimpleLoggingUtil;
import com.robo4j.hw.rpi.i2c.lidar.LidarLiteDevice;
import com.robo4j.math.geometry.ScanResult2D;
import com.robo4j.math.geometry.impl.ScanResultImpl;
import com.robo4j.units.rpi.lcd.I2CRoboUnit;
import com.robo4j.units.rpi.pwm.PCA9685ServoUnit;

/**
 * This unit controls a servo to do laser range sweep.
 * 
 * <p>
 * Configuration:
 * </p>
 * <li>
 * <ul>
 * pan: the servo to use for panning the laser. Defaults to "laserscanner.servo". Set to "null"
 * to not use a servo for panning.
 * </ul>
 * <ul>
 * servoRange: the range in degrees that send 1.0 (full) to a servo will result
 * in. This must be measured by testing your hardware. Changing configuration on
 * your servo will change this too.
 * </ul>
 * </li>
 * 
 * FIXME(Marcus/Mar 10, 2017): Only supports the pan servo for now.
 * 
 * @author Marcus Hirt (@hirt)
 * @author Miroslav Wengner (@miragemiko)
 */
public class LaserScanner extends I2CRoboUnit<ScanRequest> {
	private String pan;
	private LidarLiteDevice lidar;
	private float servoRange;
	private float angularSpeed;
	private float minimumAcquisitionTime;
	private float angularOffset;

	private final static class ScanJob implements Runnable {
		private final AtomicInteger invokeCount = new AtomicInteger(0);
		private final ScanResultImpl scanResult = new ScanResultImpl(100);
		private final ScanRequest request;
		private final RoboReference<ScanResult2D> recipient;
		private final RoboReference<Float> servo;
		private final boolean lowToHigh;
		private final int numberOfScans;
		private final int delay;
		private final float minimumServoMovementTime;
		private final float minimumAcquisitionTime;
		private final float servoRange;
		private volatile float currentAngle;
		private final LidarLiteDevice lidar;
		private volatile boolean finished = false;

		public ScanJob(boolean lowToHigh, float minimumServoMovementTime, float minimumAcquisitionTime, ScanRequest request,
				RoboReference<Float> servo, float servoRange, LidarLiteDevice lidar, RoboReference<ScanResult2D> recipient) {
			this.lowToHigh = lowToHigh;
			this.minimumServoMovementTime = minimumServoMovementTime;
			this.minimumAcquisitionTime = minimumAcquisitionTime;
			this.request = request;
			this.servo = servo;
			this.servoRange = servoRange;
			this.lidar = lidar;
			this.recipient = recipient;
			// one move, one first acquisition
			this.numberOfScans = calculateNumberOfScans() + 2;
			this.delay = calculateDelay();
			this.currentAngle = lowToHigh ? request.getStartAngle() : request.getStartAngle() + request.getRange();
		}

		@Override
		public void run() {
			int currentRun = invokeCount.incrementAndGet();
			if (currentRun == 1) {
				// On first step, only move servo to start position
				float normalizedServoTarget = getNormalizedAngle();
				servo.sendMessage(normalizedServoTarget);
			} else if (currentRun == 2) {
				// On second, just start acquisition (no point to read yet)
				startAcquisition();
			} else if (currentRun > numberOfScans) {
				doScan();
				finish();
			} else {
				doScan();
			}
			// FIXME(Marcus/Mar 10, 2017): May want to synchronize this...
			updateTargetAngle();
		}

		private void startAcquisition() {
			try {
				lidar.acquireRange();
				servo.sendMessage(getNormalizedAngle());
			} catch (IOException e) {
				SimpleLoggingUtil.error(getClass(), "Could not read laser!", e);
			}		
		}

		private float getNormalizedAngle() {
			return this.currentAngle / this.servoRange;
		}

		private void doScan() {
			// Read previous acquisition
			try {
				float readDistance = lidar.readDistance();
				// Laser was actually shooting at the previous angle, before
				// moving - recalculate for that angle
				float lastAngle = currentAngle + (lowToHigh ? -request.getStep() : request.getStep());
				scanResult.addPoint(readDistance, lastAngle);
				servo.sendMessage(getNormalizedAngle());
				lidar.acquireRange();
			} catch (IOException e) {
				SimpleLoggingUtil.error(getClass(), "Could not read laser!", e);
			}
		}

		private void finish() {
			if (!finished) {
				recipient.sendMessage(scanResult);
				finished = true;
			} else {
				SimpleLoggingUtil.error(getClass(), "Tried to scan more laser points after being finished!");
			}
		}

		private void updateTargetAngle() {
			if (lowToHigh) {
				currentAngle += request.getStep();
			} else {
				currentAngle -= request.getStep();
			}
		}

		private int calculateNumberOfScans() {
			return Math.round(request.getRange() / request.getStep());
		}

		// FIXME(Marcus/Mar 10, 2017): Calculate the required delay later from
		// physical model.
		private int calculateDelay() {
			return 10;
		}
	}

	public LaserScanner(RoboContext context, String id) {
		super(ScanRequest.class, context, id);
	}

	@Override
	protected void onInitialization(Configuration configuration) throws ConfigurationException {
		super.onInitialization(configuration);
		pan = configuration.getString("servo", "laserscanner.servo");
		// Using degrees for convenience
		servoRange = (float) configuration.getFloat("servoRange", 45.0f);

		// Using angular degrees per second.
		angularSpeed = configuration.getFloat("angularSpeed", 90.0f);

		// Minimum acquisition time, in ms
		minimumAcquisitionTime = configuration.getFloat("minAquisitionTime", 2.0f);

		// Angular offset - required since we don't wait for an acquired range
		// before moving the servo.
		// If you always move the servo very slowly, this is not required.
		// FIXME(Marcus/Feb 16, 2017): We will very likely need configuration
		// tables for this - i.e. different
		// finely tuned offsets for different angular steps?
		angularOffset = configuration.getFloat("angularOffset", 3.0f);

		try {
			lidar = new LidarLiteDevice(getBus(), getAddress());
		} catch (IOException e) {
			throw new ConfigurationException(String.format(
					"Failed to initialize lidar device. Make sure it is hooked up to bus: %d address: %xd", getBus(), getAddress()), e);
		}
	}

	@Override
	public void onMessage(ScanRequest message) {
		RoboReference<Float> servo = getReference(pan);

		RoboReference<ScanResult2D> receiverRef = getContext().getReference(message.getReceiverId());
		scheduleScan(message, servo, receiverRef);
	}

	private void scheduleScan(ScanRequest message, RoboReference<Float> servo,
			RoboReference<ScanResult2D> recipient) {
		float currentInput = getCurrentInput(servo);
		float midPoint = message.getStartAngle() + message.getRange() / 2;
		boolean lowToHigh = false;
		if (inputToAngle(currentInput) <= midPoint) {
			lowToHigh = true;
		}
		float minimumServoMovementTime = message.getRange() / angularSpeed;
		ScanJob job = new ScanJob(lowToHigh, minimumServoMovementTime, minimumAcquisitionTime, message, servo, servoRange, lidar,
				recipient);

		schedule(job);
	}

	private float inputToAngle(float currentInput) {
		return servoRange * currentInput;
	}

	private void schedule(ScanJob job) {
		long actualDelay = 2;
		// Need two extra for first servo move and initial acquisition start.
		for (int i = 0; i < job.numberOfScans + 2; i++) {
			getContext().getScheduler().schedule(job, actualDelay, TimeUnit.MILLISECONDS);
			actualDelay += job.delay;
		}
	}

	private float getCurrentInput(RoboReference<Float> servo) {
		try {
			return servo.getAttribute(PCA9685ServoUnit.ATTRIBUTE_SERVO_INPUT).get();
		} catch (InterruptedException | ExecutionException e) {
			SimpleLoggingUtil.error(getClass(), "Could not read servo input!", e);
			return 0;
		}
	}

	private <T> RoboReference<Float> getReference(String unit) {
		if (unit.equals("null")) {
			return null;
		}
		return getContext().getReference(unit);
	}

	/**
	 * if (minimumSampleAquisitionTime < minimumServoMovementTime) { // We are
	 * constrained by the servo movement speed, simply sample whilst moving //
	 * So, start with setting a servo movement from min to max (or max to min,
	 * depending on scan direction and current angle), } else { // We are
	 * constrained by the time it takes to acquire the samples. Move the servo
	 * with the calculated delays and acquire samples in between
	 * 
	 * }
	 */
}
