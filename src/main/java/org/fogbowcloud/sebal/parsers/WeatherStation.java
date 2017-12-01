package org.fogbowcloud.sebal.parsers;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.http.HttpException;
import org.apache.log4j.Logger;
import org.fogbowcloud.sebal.parsers.plugins.StationOperator;
import org.fogbowcloud.sebal.parsers.plugins.StationOperatorConstants;
import org.fogbowcloud.sebal.parsers.plugins.ftp.FTPStationOperator;
import org.fogbowcloud.sebal.util.SEBALAppConstants;
import org.json.JSONArray;
import org.json.JSONObject;

public class WeatherStation {

	private Properties properties;
	private StationOperator stationOperator;

	private static final Logger LOGGER = Logger.getLogger(WeatherStation.class);

	private static final String[] WANTED_STATION_HOURS = new String[] { "1200" };
	private static final String MIN_WIND_SPEED_VALUE = "0.3";
	private static final String MAX_WIND_SPEED_VALUE = "31.0";
	private static final int MIN_STATION_RECORDS = 3;

	public WeatherStation() throws URISyntaxException, HttpException, IOException {
		this(new Properties());
	}

	public WeatherStation(Properties properties)
			throws URISyntaxException, HttpException, IOException {
		this(properties, new FTPStationOperator(properties));
	}

	protected WeatherStation(Properties properties, FTPStationOperator stationOperator) {
		this.properties = properties;
		this.stationOperator = stationOperator;
	}

	public String getStationData(double lat, double lon, Date date, String sceneCenterTime) {
		LOGGER.debug("latitude: " + lat + " longitude: " + lon + " date: " + date);

		int daysWindow = 0;
		List<JSONObject> nearStations = this.stationOperator.findNearestStation(date, lat, lon,
				daysWindow);

		String stationData = null;
		if (nearStations != null) {
			stationData = this.selectStation(date, nearStations, daysWindow, sceneCenterTime);
		}
		return stationData;
	}

	protected String selectStation(Date date, List<JSONObject> stations, int numberOfDays,
			String sceneCenterTime) {

		Date begindate = new Date(date.getTime() - numberOfDays * StationOperatorConstants.A_DAY);
		Date endDate = new Date(date.getTime() + numberOfDays * StationOperatorConstants.A_DAY);

		if (stations != null && !stations.isEmpty()) {
			LOGGER.debug("beginDate: " + begindate + " endDate: " + endDate);

			for (JSONObject station : stations) {
				try {
					JSONArray stationData = this.stationOperator.readStation(
							station.optString("id"),
							StationOperatorConstants.DATE_FORMAT.format(begindate),
							StationOperatorConstants.DATE_FORMAT.format(endDate));

					Double stationDistance = station.optDouble("distance");

					stationData = windSpeedCorrection(stationData);

					if (checkRecords(stationData)) {
						LOGGER.info("Founded Station Data: " + System.lineSeparator()
								+ stationData.toString());
						LOGGER.info("Station distance: [" + stationDistance + "]km");

						return generateStationData(stationData, stationDistance);
					}
				} catch (Exception e) {
					LOGGER.error("Error while reading full record", e);
				}
			}
		} else {
			LOGGER.info("Stations list is empty");
		}

		return null;
	}

	protected boolean checkRecords(JSONArray stationData) {
		boolean result = false;
		if (stationData != null) {

			for (int i = 0; i < stationData.length(); i++) {
				JSONObject stationDataRecord = stationData.optJSONObject(i);

				if (!containsNeededStationValues(stationDataRecord)) {
					stationData.remove(i);
					i--;
				}
			}

			if (stationData.length() >= WeatherStation.MIN_STATION_RECORDS) {
				boolean hasAll = true;
				for (String hour : WeatherStation.WANTED_STATION_HOURS) {
					if (!hasRecord(stationData, SEBALAppConstants.JSON_STATION_TIME, hour)) {
						hasAll = false;
					}
				}
				result = hasAll;
			}
		}
		return result;
	}

	private boolean hasRecord(JSONArray stationData, String key, String value) {
		boolean result = false;
		for (int i = 0; i < stationData.length() && !result; i++) {
			JSONObject stationDataRecord = stationData.optJSONObject(i);

			if (stationDataRecord.optString(key).equals(value)) {
				result = true;
			}
		}
		return result;
	}

	protected JSONArray windSpeedCorrection(JSONArray stationData) {
		JSONArray result = new JSONArray();

		if (stationData != null) {
			JSONArray adjustedStationData = new JSONArray(stationData.toString());

			for (int i = 0; i < adjustedStationData.length(); i++) {
				JSONObject stationDataRecord = adjustedStationData.optJSONObject(i);

				Double JSONWindSpeed = Double.parseDouble(
						stationDataRecord.optString(SEBALAppConstants.JSON_STATION_WIND_SPEED));
				Double minWindSpeed = Double.parseDouble(WeatherStation.MIN_WIND_SPEED_VALUE);
				Double maxWindSpeed = Double.parseDouble(WeatherStation.MAX_WIND_SPEED_VALUE);

				if (JSONWindSpeed < minWindSpeed) {
					stationDataRecord.remove(SEBALAppConstants.JSON_STATION_WIND_SPEED);

					stationDataRecord.put(SEBALAppConstants.JSON_STATION_WIND_SPEED,
							WeatherStation.MIN_WIND_SPEED_VALUE);

				} else if (JSONWindSpeed > maxWindSpeed) {
					adjustedStationData.remove(i);
					i--;
				}
			}

			result = adjustedStationData;
		}

		return result;
	}

	protected boolean containsNeededStationValues(JSONObject data) {
		String[] neededStationValues = new String[] { SEBALAppConstants.JSON_STATION_DATE,
				SEBALAppConstants.JSON_STATION_TIME, SEBALAppConstants.JSON_STATION_LATITUDE,
				SEBALAppConstants.JSON_STATION_LONGITUDE, SEBALAppConstants.JSON_AIR_TEMPERATURE,
				SEBALAppConstants.JSON_DEWPOINT_TEMPERATURE,
				SEBALAppConstants.JSON_STATION_WIND_SPEED };

		boolean result = true;
		for (String value : neededStationValues) {
			if (data.optString(value).isEmpty() == true) {
				result = false;
			}
		}

		return result;
	}

	private String generateStationData(JSONArray stationData, Double stationDistance) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < stationData.length(); i++) {
			result.append(
					checkVariablesAndBuildString(stationData.optJSONObject(i), stationDistance));
		}
		return result.toString().trim();
	}

	protected String checkVariablesAndBuildString(JSONObject stationDataRecord,
			Double stationDistance) {

		String stationId = stationDataRecord.optString(SEBALAppConstants.JSON_STATION_ID);
		String dateValue = stationDataRecord.optString(SEBALAppConstants.JSON_STATION_DATE);
		String timeValue = stationDataRecord.optString(SEBALAppConstants.JSON_STATION_TIME);
		String latitude = stationDataRecord.optString(SEBALAppConstants.JSON_STATION_LATITUDE);
		String longitude = stationDataRecord.optString(SEBALAppConstants.JSON_STATION_LONGITUDE);
		String windSpeed = stationDataRecord.optString(SEBALAppConstants.JSON_STATION_WIND_SPEED);
		String airTemp = stationDataRecord.optString(SEBALAppConstants.JSON_AIR_TEMPERATURE);
		String dewTemp = stationDataRecord.optString(SEBALAppConstants.JSON_DEWPOINT_TEMPERATURE);
		String avgAirTemp = stationDataRecord.optString(SEBALAppConstants.JSON_AVG_AIR_TEMPERATURE);
		String relativeHumidity = stationDataRecord
				.optString(SEBALAppConstants.JSON_RELATIVE_HUMIDITY);
		String minTemp = stationDataRecord.optString(SEBALAppConstants.JSON_MIN_TEMPERATURE);
		String maxTemp = stationDataRecord.optString(SEBALAppConstants.JSON_MAX_TEMPERATURE);
		String solarRad = stationDataRecord.optString(SEBALAppConstants.JSON_SOLAR_RADIATION);
		String stationDist = stationDistance.toString();

		stationId = stationDataCorrection(stationId);
		avgAirTemp = stationDataCorrection(avgAirTemp);
		relativeHumidity = stationDataCorrection(relativeHumidity);
		minTemp = stationDataCorrection(minTemp);
		maxTemp = stationDataCorrection(maxTemp);
		solarRad = stationDataCorrection(solarRad);

		return stationId + ";" + dateValue + ";" + timeValue + ";" + latitude + ";" + longitude
				+ ";" + windSpeed + ";" + airTemp + ";" + dewTemp + ";" + avgAirTemp + ";"
				+ relativeHumidity + ";" + minTemp + ";" + maxTemp + ";" + solarRad + ";"
				+ stationDist + ";" + System.lineSeparator();
	}

	private String stationDataCorrection(String data) {
		if (data.isEmpty() || data == null) {
			data = new String("NA");
		}
		return data;
	}

	public Properties getProperties() {
		return this.properties;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}
}
