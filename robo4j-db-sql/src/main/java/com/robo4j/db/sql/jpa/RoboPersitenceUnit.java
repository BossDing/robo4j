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

package com.robo4j.db.sql.jpa;

import com.robo4j.AttributeDescriptor;
import com.robo4j.ConfigurationException;
import com.robo4j.DefaultAttributeDescriptor;
import com.robo4j.RoboContext;
import com.robo4j.RoboReference;
import com.robo4j.RoboUnit;
import com.robo4j.configuration.Configuration;
import com.robo4j.db.sql.SQLDataSourceUnit;
import com.robo4j.db.sql.dto.ERoboDbContract;
import com.robo4j.db.sql.model.ERoboEntity;
import com.robo4j.db.sql.model.ERoboUnit;
import com.robo4j.db.sql.support.RoboRequestType;
import com.robo4j.db.sql.util.DBSQLConstants;
import com.robo4j.logging.SimpleLoggingUtil;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * @author Marcus Hirt (@hirt)
 * @author Miro Wengner (@miragemiko)
 */
public abstract class RoboPersitenceUnit<T> extends RoboUnit<T> {

	private static final int DEFAULT_INDEX = 0;
	private static final String CONST_COLON = ":";
	private static final String CONST_COMMA = ",";
	private Map<String, Object> entityMap = new WeakHashMap<>();
	private SQLDataSourceUnit dataSourceUnit;

	/**
	 * Constructor
	 */
	public RoboPersitenceUnit(Class<T> messageType, RoboContext context, String id) {
		super(messageType, context, id);
	}

	@Override
	public void initialize(Configuration configuration) throws ConfigurationException {
		registerUnit(configuration);
		// super.initialize(configuration);
	}

	protected ERoboUnit getEntity() {
		List<ERoboUnit> tmpList = dataSourceUnit.getByMap(ERoboUnit.class, entityMap);
		return tmpList.isEmpty() ? null : tmpList.get(DEFAULT_INDEX);
	}

	protected void save(ERoboUnit unit) {
		ERoboDbContract request = getERequest(unit);
		dataSourceUnit.onMessage(request);
	}

	private ERoboDbContract getERequest(ERoboUnit unit){
		ERoboDbContract result = new ERoboDbContract(RoboRequestType.SAVE);
		result.addData(unit.getClass(), unit);
		return result;
	}

	private void registerUnit(Configuration configuration) throws ConfigurationException {
		String tmpName = configuration.getString(DBSQLConstants.KEY_PERSISTENCE_UNIT, null);
		if (tmpName == null) {
			throw ConfigurationException.createMissingConfigNameException(DBSQLConstants.KEY_PERSISTENCE_UNIT);
		}
		RoboReference<ERoboEntity<?>> sqlUnitReference = getContext().getReference(tmpName);
		if (sqlUnitReference == null) {
			throw ConfigurationException.createMissingConfigNameException(DBSQLConstants.KEY_PERSISTENCE_UNIT);
		}
		AttributeDescriptor<SQLDataSourceUnit> descriptor = DefaultAttributeDescriptor.create(SQLDataSourceUnit.class,
				"robo_sql_unit");
		entityMap.put("uid", getId());
		try {
			dataSourceUnit = sqlUnitReference.getAttribute(descriptor).get();
		} catch (InterruptedException | ExecutionException e) {
			SimpleLoggingUtil.error(getClass(), "error", e);
		}

		ERoboUnit entity = getEntity();
		if(entity == null){
			entity = new ERoboUnit();
			entity.setUid(getId());
			String configString = configuration.getValueNames().stream().map(n -> {
				StringBuilder sb = new StringBuilder();
				sb.append(n);
				sb.append(CONST_COLON);
				sb.append(configuration.getValue(n, null));
				return sb.toString();
			}).collect(Collectors.joining(CONST_COMMA));
			entity.setConfig(configString);
			dataSourceUnit.onMessage(getERequest(entity));
		} else {
			SimpleLoggingUtil.debug(getClass(), "robo_unit exists: " + entity);
		}

	}

}
