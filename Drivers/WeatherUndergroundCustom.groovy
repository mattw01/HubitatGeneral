/**
 *  WeatherUndergroundCustom
 *
 *  Copyright 2018 mattw01
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
    definition (name: "WeatherUndergroundCustom", namespace: "mattw01", author: "mattw01") {
        capability "Actuator"
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Illuminance Measurement"
        capability "Relative Humidity Measurement"
        
        command "poll"
        command "forcePoll"
        
        attribute "solarradiation", "number"
        attribute "observation_time", "string"
        attribute "weather", "string"
        attribute "feelsLike", "number"
        attribute "precip_1hr_in", "number"
        attribute "precip_today_in", "number"
        attribute "wind_mph", "number"
        attribute "wind_string", "string"
        attribute "pressure_in", "number"
        attribute "dewpoint_f", "number"
        attribute "UV", "number"
        attribute "visibility_mi", "number"
        attribute "forecastHigh", "number"
        attribute "forecastLow", "number"
        attribute "forecastConditions", "string"
        
    }
    preferences() {
        section("Query Inputs"){
            input "apiKey", "text", required: true, title: "API Key"
            input "pollLocation", "text", required: true, title: "ZIP Code or Location"
            input "pollIntervalLimit", "number", title: "Poll Interval Limit:", required: true
            input "autoPoll", "bool", required: false, title: "Enable Auto Poll"
            input "pollInterval", "enum", title: "Auto Poll Interval:", required: false, defaultValue: "5 Minutes",
                   options: ["5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes", "1 Hour", "3 Hours"]

        }
    }
}

def updated() {
    log.debug "updated called"
    unschedule()
    forcePoll()
    def pollIntervalCmd = (settings?.pollInterval ?: "5 Minutes").replace(" ", "")
    if(autoPoll)
        "runEvery${pollIntervalCmd}"(pollSchedule)
}
def pollSchedule()
{
    forcePoll()
}
              
def parse(String description) {
}

def poll()
{
    if(now() - state.lastPoll > (pollIntervalLimit * 60000))
        forcePoll()
    else
        log.debug "poll called before interval threshold was reached"
}

def forcePoll()
{
    log.debug "WU: forcePoll called"
    def params = [
        uri: "http://api.wunderground.com/api/${apiKey}/conditions/forecast/q/${pollLocation}.json"
    ]
    log.debug "params: ${params}"
    try {
        httpGet(params) { resp ->
            resp.headers.each {
            log.debug "Response: ${it.name} : ${it.value}"
        }
        log.debug "response contentType: ${resp.contentType}"
        log.debug "response data: ${resp.data}"
            sendEvent(name: "temperature", value: resp.data.current_observation.temp_f, unit: "F")
            sendEvent(name: "solarradiation", value: resp.data.current_observation.solarradiation, unit: "W")
            sendEvent(name: "humidity", value: resp.data.current_observation.relative_humidity, unit: "%")
            // Map solarradiation to illuminance for now
            sendEvent(name: "illuminance", value: resp.data.current_observation.solarradiation, unit: "lux")
            sendEvent(name: "observation_time", value: resp.data.current_observation.observation_time)
            sendEvent(name: "weather", value: resp.data.current_observation.weather)
            sendEvent(name: "feelsLike", value: resp.data.current_observation.feelslike_f, unit: "F")
            sendEvent(name: "precip_1hr_in", value: resp.data.current_observation.precip_1hr_in, unit: "IN")
            sendEvent(name: "precip_today_in", value: resp.data.current_observation.precip_today_in, unit: "IN")
            sendEvent(name: "wind_string", value: resp.data.current_observation.wind_string)
            sendEvent(name: "wind_mph", value: resp.data.current_observation.wind_mph, unit: "MPH")
            sendEvent(name: "pressure_in", value: resp.data.current_observation.pressure_in, unit: "mi")
            sendEvent(name: "dewpoint_f", value: resp.data.current_observation.dewpoint_f, unit: "F")
            sendEvent(name: "UV", value: resp.data.current_observation.UV)
            sendEvent(name: "visibility_mi", value: resp.data.current_observation.visibility_mi, unit: "mi")
            
            sendEvent(name: "forecastHigh", value: resp.data.forecast.simpleforecast.forecastday[0].high.fahrenheit, unit: "F")
            sendEvent(name: "forecastLow", value: resp.data.forecast.simpleforecast.forecastday[0].low.fahrenheit, unit: "F")
            sendEvent(name: "forecastConditions", value: resp.data.forecast.simpleforecast.forecastday[0].conditions)
            state.lastPoll = now()
        }
    } catch (e) {
        log.error "something went wrong: $e"
    }
    
}