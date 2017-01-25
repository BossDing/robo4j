/*
 * Copyright (C) 2016. Miroslav Wengner, Marcus Hirt
 * This RpiMotorUtil.java  is part of robo4j.
 * module: robo4j-rpi
 *
 * robo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * robo4j is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with robo4j .  If not, see <http://www.gnu.org/licenses/>.
 */

package com.robo4j.rpi.util;

/**
 * @author Marcus Hirt (@hirt)
 * @author Miroslav Wengner (@miragemiko)
 * @since 17.12.2016
 */
public final class RpiMotorUtil {

	public static byte[] createCommand(int motor, int speed, int direction) {
		byte m = (byte) motor;
		byte s = (byte) speed;
		byte d = (byte) direction;
		return new byte[] { m, s, d };
	}

}
