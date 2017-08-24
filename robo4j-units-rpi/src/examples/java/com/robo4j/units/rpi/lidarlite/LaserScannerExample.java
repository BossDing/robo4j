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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Robo4J. If not, see <http://www.gnu.org/licenses/>.
 */
package com.robo4j.units.rpi.lidarlite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.robo4j.core.RoboBuilder;
import com.robo4j.core.RoboBuilderException;
import com.robo4j.core.RoboContext;
import com.robo4j.core.RoboReference;
import com.robo4j.core.configuration.Configuration;
import com.robo4j.core.configuration.ConfigurationFactory;
import com.robo4j.units.rpi.pwm.ServoUnitExample;

/**
 * Runs the laser scanner, printing the max range and min range found on stdout.
 * (To see all data, run with JFR and dump a recording.)
 * 
 * @author Marcus Hirt (@hirt)
 * @author Miroslav Wengner (@miragemiko)
 */
public class LaserScannerExample {

	private static final String DEFAULT_LIDAREXAMPLE_XML = "lidarexample.xml";

	public static void main(String[] args) throws RoboBuilderException, IOException {
		float startAngle = -45.0f;
		float range = 90.0f;
		float step = 1.0f;

		String inputFileName = null;
		switch (args.length){
			case 1:
				inputFileName = args[0];
				break;
			case 3:
				startAngle = Float.parseFloat(args[0]);
				range = Float.parseFloat(args[1]);
				step = Float.parseFloat(args[2]);
				break;
			case 4:
				inputFileName = args[0];
				startAngle = Float.parseFloat(args[0]);
				range = Float.parseFloat(args[1]);
				step = Float.parseFloat(args[2]);
				break;
			default:
				System.out.println("default configuration");
				break;
		}

		Configuration controllerConfiguration = ConfigurationFactory.createEmptyConfiguration();
		controllerConfiguration.setFloat(LaserScannerTestController.CONFIG_KEY_START_ANGLE, startAngle);
		controllerConfiguration.setFloat(LaserScannerTestController.CONFIG_KEY_RANGE, range);
		controllerConfiguration.setFloat(LaserScannerTestController.CONFIG_KEY_STEP, step);

		System.out.println(String.format("Running scans with startAngle=%2.1f, range=%2.1f and step=%2.1f", startAngle, range, step));
		
		RoboBuilder builder = new RoboBuilder();

		InputStream settings = loadPropertyFile(inputFileName);
		if (settings == null) {
			System.out.println("Could not find the settings for the LaserScannerExample!");
			System.exit(2);
		}
		builder.add(settings).add(LaserScannerTestController.class, controllerConfiguration, "controller").add(LaserScanProcessor.class,
				"processor");
		RoboContext ctx = builder.build();
		RoboReference<String> reference = ctx.getReference("controller");
		ctx.start();
		System.out.println("Starting scanning for ever\nPress enter to quit");
		reference.sendMessage("scan");
		System.in.read();
	}

	//Private Methods
	private static InputStream loadPropertyFile(String fileName) throws IOException {
		if(fileName != null){
			Path filePath = Paths.get(fileName);
			return Files.newInputStream(filePath);
		} else {
			return ServoUnitExample.class.getClassLoader().getResourceAsStream(DEFAULT_LIDAREXAMPLE_XML);
		}
	}


}
