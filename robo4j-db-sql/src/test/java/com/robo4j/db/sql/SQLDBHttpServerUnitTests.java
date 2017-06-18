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

package com.robo4j.db.sql;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import javax.transaction.Transactional;

import com.robo4j.db.sql.util.DBSQLConstants;
import org.junit.Assert;
import org.junit.Test;
import org.postgresql.util.PSQLException;

import com.robo4j.core.AttributeDescriptor;
import com.robo4j.core.DefaultAttributeDescriptor;
import com.robo4j.core.RoboSystem;
import com.robo4j.core.configuration.Configuration;
import com.robo4j.core.configuration.ConfigurationFactory;
import com.robo4j.core.util.SystemUtil;
import com.robo4j.db.sql.dto.ERoboPointDTO;
import com.robo4j.db.sql.model.ERoboEntity;
import com.robo4j.db.sql.model.ERoboPoint;
import com.robo4j.db.sql.model.ERoboUnit;

/**
 * DB SQL unit attached to other units to safe entities
 *
 * @author Marcus Hirt (@hirt)
 * @author Miro Wengner (@miragemiko)
 */
@SuppressWarnings(value = { "unchecked", "rawtypes" })
public class SQLDBHttpServerUnitTests {

	private static final String DB_PROVIDER = "h2";
	private static final int DEFAULT_INDEX = 0;

	@Test
	@Transactional(rollbackOn = PSQLException.class)
	public void createSystem() throws Exception {

		final int maxPoints = 3;
		final String targetUnit = "testSystem";
		final String dataSourceName = "dbSQLUnit";
		final RoboSystem system = new RoboSystem();
		Configuration sqlConfig = ConfigurationFactory.createEmptyConfiguration();

		SQLDataSourceUnit sqlUnit = new SQLDataSourceUnit(system, dataSourceName);
		sqlConfig.setString("sourceType", DB_PROVIDER);
		sqlConfig.setString("packages", "com.robo4j.db.sql.model");
		sqlConfig.setInteger("limit", 3);
		sqlConfig.setString("sorted", "asc");
		sqlConfig.setString("hibernate.hbm2ddl.auto", "create");
		sqlConfig.setString("targetUnit", targetUnit);

		RoboPointSQLPersistenceUnit roboPointSQLPersistenceUnit = new RoboPointSQLPersistenceUnit(system, targetUnit);
		Configuration testConfig = ConfigurationFactory.createEmptyConfiguration();
		testConfig.setString(DBSQLConstants.KEY_PERSISTENCE_UNIT, dataSourceName);
		testConfig.setString("config", "magic config");

		/* specific configuration */
		system.addUnits(sqlUnit, roboPointSQLPersistenceUnit);
		sqlUnit.initialize(sqlConfig);
		roboPointSQLPersistenceUnit.initialize(testConfig);

		system.start();

		System.out.println("System: State after start:");
		System.out.println(SystemUtil.printStateReport(system));

		IntStream.range(DEFAULT_INDEX, maxPoints)
				.forEach(i -> roboPointSQLPersistenceUnit.onMessage(new ERoboPointDTO("testType" + i, "testValue" + i)));

		AttributeDescriptor<List> descriptorAllPoints = DefaultAttributeDescriptor.create(List.class, "unit_points");
		List<ERoboPoint> allPointsList = (List<ERoboPoint>) sqlUnit.onGetAttribute(descriptorAllPoints);

		Map<String, Object> tmpMap = new HashMap<>();
		tmpMap.put("unit", targetUnit);
		List<ERoboPoint> directBySQLUnit = sqlUnit.getByMap(ERoboPoint.class, tmpMap);

		AttributeDescriptor<List> descriptorAllUnits = DefaultAttributeDescriptor.create(List.class, "units_all_desc");
		List<ERoboEntity<Long>> allUnitsList = (List<ERoboEntity<Long>>) sqlUnit.onGetAttribute(descriptorAllUnits);

		Assert.assertNotNull(directBySQLUnit);
		Assert.assertNotNull(allPointsList);
		Assert.assertNotNull(allUnitsList);

		Assert.assertTrue(allUnitsList.size() == 1);
		Assert.assertTrue(((ERoboUnit) allUnitsList.get(DEFAULT_INDEX)).getUid().equals(targetUnit));
		Assert.assertTrue(directBySQLUnit.size() == allPointsList.size());

		Assert.assertTrue(allPointsList.size() == maxPoints);
		IntStream.range(DEFAULT_INDEX, directBySQLUnit.size())
				.forEach(i -> Assert.assertTrue(directBySQLUnit.get(i).equals(allPointsList.get(i))));

		system.shutdown();
		System.out.println("System: State after shutdown:");
		System.out.println(SystemUtil.printStateReport(system));

	}

}