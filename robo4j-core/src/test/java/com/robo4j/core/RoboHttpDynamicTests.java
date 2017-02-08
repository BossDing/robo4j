/*
 * Copyright (C) 2014, 2017. Miroslav Wengner, Marcus Hirt
 * This RoboHttpDynamicTests.java  is part of robo4j.
 * module: robo4j-core
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

package com.robo4j.core;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.robo4j.core.client.util.ClientClassLoader;
import com.robo4j.core.configuration.Configuration;
import com.robo4j.core.configuration.ConfigurationFactory;
import com.robo4j.core.unit.HttpUnit;
import com.robo4j.core.util.SystemUtil;

/**
 *
 * Dynamic HttpUnit request/method configuration
 *
 * @author Marcus Hirt (@hirt)
 * @author Miro Wengner (@miragemiko)
 */
public class RoboHttpDynamicTests {

	private static final int PORT = 8025;

	@Test
	public void simpleHttpNonUnitTest() throws ConfigurationException, IOException {
		RoboSystem system = new RoboSystem();
		Configuration config = ConfigurationFactory.createEmptyConfiguration();

		HttpUnit httpDynamic = new HttpUnit(system, "http_dynamic");
		config.setString("target", "request_consumer");
		config.setInteger("port", PORT);

		/* specific configuration */
		config.setInteger("pathsNumber", 1);
		config.setString("path_0", "test");
		config.setString("method_0", "GET");
		config.setInteger("pathCommands_0", 1);
		config.setString("commandName_0_0", "command");
		config.setString("commandValues_0_0", "right,left,move,back,enter");
		httpDynamic.initialize(config);

		StringConsumer consumer = new StringConsumer(system, "request_consumer");

		Assert.assertNotNull(system.getUnits());
		Assert.assertEquals(system.getUnits().size(), 0);
		Assert.assertEquals(httpDynamic.getState(), LifecycleState.INITIALIZED);
		Assert.assertEquals(system.getState(), LifecycleState.UNINITIALIZED);

		system.addUnits(httpDynamic, consumer);

		System.out.println("State before start:");
		System.out.println(SystemUtil.generateStateReport(system));
		system.start();

		System.out.println("State after start:");
		System.out.println(SystemUtil.generateStateReport(system));

		System.out.println("RoboSystem http server\n\tPort:" + PORT + "\n");
		System.out.println("Usage:\n\tRequest GET: http://<IP_ADDRESS>:" + PORT + "/test?command=enter");
		System.out.println("\tRequest command types: right,left,move,back,enter\n");

		System.out.println("Going Down!");
		// System.in.read();
		system.stop();
		system.shutdown();
		System.out.println("System is Down!");
		Assert.assertNotNull(system.getUnits());
		Assert.assertEquals(system.getUnits().size(), 2);
		Assert.assertEquals(consumer.getReceivedMessages().size(), 0);
	}

	@Test
	public void simpleHttpDeclarative() throws RoboBuilderException, IOException {

		RoboBuilder builder = new RoboBuilder().add(ClientClassLoader.getInstance().getResource("http_get.xml"));
		RoboContext ctx = builder.build();

		Configuration configuration = ctx.getReference("http").getConfiguration().getChildConfiguration("commands");
		Assert.assertNotNull(configuration.getValueNames());
		Assert.assertEquals(configuration.getValueNames().size(), 5);
		Assert.assertEquals(configuration.getString("up", null), "move");


	}
}