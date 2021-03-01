/**
 *  Activity Viewer
 *
 *  Copyright 2018 mattw
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

/////////////////// koalyptus/TableFilter LICENSE ///////////////////
/**
 * The MIT License (MIT)
 * 
 * Copyright (c) 2015 Max Guglielmi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

 // Activity Viewer Changelog
 // V 1.0 Initial release
 
def ignoredEvents() { return [ 'lastReceive' , 'reachable' , 
                         'buttonReleased' , 'buttonPressed', 'lastCheckinDate', 'lastCheckin', 'buttonHeld' ] }

def version() { return "v1.0" }
definition(
    name: "Activity Viewer",
    namespace: "mattw",
    author: "mattw",
    description: "View Hub Activity",
    category: "",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
)


preferences {
  page(name:"mainPage")
  page(name:"devicesPage")
  page(name:"disableAPIPage")
  page(name:"enableAPIPage")

mappings {

  path("/activity/") {
    action: [
      GET: "getActivity"
    ]
  }
  path("/timeline/") {
    action: [
      GET: "getTimeline"
    ]
  }
}
}
def installed() {
    log.debug "Installed with settings: ${settings}"

    initialize()
}
def uninstalled() {
    if (state.endpoint) {
        try {
            logDebug "Revoking API access token"
            revokeAccessToken()
        }
        catch (e) {
            log.warn "Unable to revoke API access token: $e"
        }
    }
}
def updated() {
    log.debug "Activity Viewer updated with the following settings:\n${settings}"
    log.debug "##########################################################################################"
    log.debug "secret = \"${state.endpointSecret}\""
    log.debug "smartAppURL = \"${state.endpointURL}\""
    log.debug "##########################################################################################"

    unsubscribe()
    initialize()
}

def initialize() {
}

private buildActivityMap() {
    def resp = []
    def today = new Date()
    def then = today - 1
    log.debug "today " + today
    log.debug "then " + then
    if(actuators) {
        actuators.each {
        resp << it?.eventsBetween(then, today, [max: 200])?.findAll{"$it.source" == "DEVICE"}?.collect{[description: it.description, descriptionText: it.descriptionText, displayName: it.displayName, date: it.date, name: it.name, unit: it.unit, source: it.source, value: it.value]}
        }
    }
    if(sensors) {
        sensors.each {
        resp << it?.eventsBetween(then, today, [max: 200])?.collect{[description: it.description, descriptionText: it.descriptionText, displayName: it.displayName, date: it.date, name: it.name, unit: it.unit, source: it.source, value: it.value]}
        }
    }
    //if(location) {
    //    def today = new Date()
    //    def then = today - 1
    //    resp << location?.eventsBetween(then, today, [max: 200])?.collect{[description: it.description, descriptionText: it.descriptionText, displayName: it.displayName, date: it.date, name: it.name, unit: it.unit, source: it.source, value: it.value]}
    //}
    resp.flatten().sort{ it.date }
}

def buildTimeline() {
    def deviceState = [:]
    def timeline = []
    
    def activity = buildActivityMap()
    
    activity.each {
        checkTimelineEvent(timeline, deviceState, it, 'motion',   'active', 'inactive')
        checkTimelineEvent(timeline, deviceState, it, 'switch',   'on',      'off')
        checkTimelineEvent(timeline, deviceState, it, 'contact',  'open',    'closed')
        checkTimelineEvent(timeline, deviceState, it, 'presence', 'present', 'not present')
    }
    
    // Clean up incomplete events
    deviceState.each {
        if(it?.value) {
            if(it.value.displayName)
                timeline << [ name: it.value.displayName, start: it.value.start, end: new Date(now()) ]
        }
    }
    timeline
}

// Check each event history and add to timeline if a start and end are found
def checkTimelineEvent(timeline, deviceState, currentEvent, type, startValue, endValue) {
    def mapBase = "${currentEvent.displayName}_${type}"
    // Check if this is the right type (motion, switch, etc)
    if(currentEvent.name == type) {
        // Check if this matches the start value (ie motion, on)
        if(currentEvent.value == startValue) {
            deviceState[mapBase] = [ displayName : currentEvent.displayName, 
                                    value: currentEvent.value, start: currentEvent.date ]
        }
        // Check if this matches the end value (ie inactive, off)
        if(currentEvent.value == endValue) {
            // Check if we have a start value
            if(deviceState[mapBase]?.value == startValue) {
                // Make a new timeline event!
                timeline << [ name: currentEvent.displayName, start: deviceState[mapBase].start, end: currentEvent.date ]
                deviceState[mapBase] = null
            }
        }
    }
}  

def getTimeline() {
    
    def timeline = buildTimeline()
    def html = """
    <html>
      <head>
        <script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>
        <script type="text/javascript">
          google.charts.load('current', {'packages':['timeline']});
          google.charts.setOnLoadCallback(drawChart);
          function drawChart() {
            var container = document.getElementById('timeline');
            var chart = new google.visualization.Timeline(container);
            var dataTable = new google.visualization.DataTable();

            dataTable.addColumn({ type: 'string', id: 'Sensor' });
            dataTable.addColumn({ type: 'date', id: 'Start' });
            dataTable.addColumn({ type: 'date', id: 'End' });
            dataTable.addRows([
    """
    for(def i = 0; i < timeline.size(); i++) {
        html += "[ '${timeline[i].name}', ${getDateString(timeline[i].start)}, ${getDateString(timeline[i].end)} ]"
        if(i < timeline.size() - 1)
        html += ","
    }
    html += """]);

            chart.draw(dataTable);
          }
        </script>
      </head>
      <body>
        <div id="timeline" style="height: 800px;"></div>
      </body>
    </html>
    """
    render contentType: "text/html", data: html
}

// Create a formatted date object string for Google Charts Timeline
def getDateString(date) {
    //def dateObj = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", date.toString())
    def dateObj = date
    def year = dateObj.getYear() + 1900
    def dateString = "new Date(${year}, ${dateObj.getMonth()}, ${dateObj.getDate()}, ${dateObj.getHours()}, ${dateObj.getMinutes()}, ${dateObj.getSeconds()})"
    dateString
}
    
def getActivity(){ 
    log.debug "getActivity called"
    def finalEvents = buildActivityMap().reverse()
    //return finalEvents
    
    def html = """
    <html><head>
    <style>.dateCell{white-space:nowrap;}</style>
    <script src="https://cdn.rawgit.com/koalyptus/TableFilter/master/dist/tablefilter/tablefilter.js"></script>

    </head><body style="font-family:Arial;width:100%">
    <table class="activity-table" style="width:100%">
    <thead>
    <th>Date</th>
    <th>Device</th>
    <th>Name</th>
    <th>Value</th>
    </thead><tbody>"""
    finalEvents.each {
        if(!filterEvent(it))
        html += "<tr><td class=\"dateCell\">$it.date</td><td class=\"dateCell\">$it.displayName</td><td>$it.name</td><td>$it.value</td></tr>"
    }
    html += "</tbody></table>"
    html += """
        <script>
            var filtersConfig = {
            base_path: 'https://cdn.rawgit.com/koalyptus/TableFilter/master/dist/tablefilter/',
            alternate_rows: true,
            popup_filters: true,
            rows_counter: true,
            btn_reset: true,
            status_bar: true,
            col_1: 'multiple',
            col_2: 'multiple',
            col_3: 'select'
        };
        var tf = new TableFilter(document.querySelector('.activity-table'), filtersConfig);
        tf.init();
        </script>
    </body></html>"""

    render contentType: "text/html", data: html
}
    
private filterEvent(evt) {
    ignoredEvents().contains(evt?.name)
}

private mainPage() {
    dynamicPage(name: "mainPage", uninstall:true, install:true) {
        if(!state.endpoint) {
            section("API Setup") {
                paragraph "API has not been setup. Tap below to enable it."
                href name: "enableAPIPageLink", title: "Enable API", description: "", page: "enableAPIPage"
                }
        }
        if(state.endpoint) {
            section("Local Activity Viewer Links") {
                href url: "${state.localEndpointURL}activity/?access_token=${state.endpointSecret}", title: "Activity"
                href url: "${state.localEndpointURL}timeline/?access_token=${state.endpointSecret}", title: "Timeline"
            }
            section("Remote Activity Viewer Links") {
                href url: "${state.remoteEndpointURL}activity/?access_token=${state.endpointSecret}", title: "Activity"
                href url: "${state.remoteEndpointURL}timeline/?access_token=${state.endpointSecret}", title: "Timeline"
            }
        }
        section("Device Setup") {
            href name: "devicesPageLink", title: "Select Devices", description: "", page: "devicesPage"
        }
        if (state.endpoint) {
            section("API Information") {
                        paragraph "Local Base URL:\n${state.localEndpointURL}"
                        paragraph "Remote Base URL:\n${state.remoteEndpointURL}"
                        paragraph "Secret:\n${state.endpointSecret}"
                        href "disableAPIPage", title: "Disable API", description: ""
                }
        }
        
        section("Version Information") {
            paragraph "Activity Viewer " + version()
        }
    }
}

def disableAPIPage() {
    dynamicPage(name: "disableAPIPage", title: "") {
        section() {
            if (state.endpoint) {
                try {
                    revokeAccessToken()
                }
                catch (e) {
                    log.debug "Unable to revoke access token: $e"
                }
                state.endpoint = null
            }
            paragraph "It has been done. Your token has been REVOKED. Tap Done to continue."
        }
    }
}

def enableAPIPage() {
    dynamicPage(name: "enableAPIPage") {
        section() {
            if (initializeAppEndpoint()) {
                paragraph "The API is now enabled. Click Done to continue"
            }
            else {
                paragraph "It looks like OAuth is not enabled. Please login to your IDE, click the Apps Code menu item, click the app to edit. Then click the OAuth button followed by the 'Enable OAuth' button. Click the Update button and tap Done here.", title: "Looks like we have to enable OAuth still", required: true, state: null
            }
        }
    }
}

def devicesPage() {
    dynamicPage(name:"devicesPage") {
        section ("Choose Devices") {
            input "actuators", "capability.actuator",
                title: "Which Actuators?",
                multiple: true,
                hideWhenEmpty: false,
                required: false
            input "sensors", "capability.sensor",
                title: "Which Sensors?",
                multiple: true,
                hideWhenEmpty: false,
                required: false             
        }
    }
}

private initializeAppEndpoint() {
    if (!state.endpoint) {
        try {
            def accessToken = createAccessToken()
            if (accessToken) {
                state.endpoint = getApiServerUrl()
                state.localEndpointURL = fullLocalApiServerUrl("")  
                state.remoteEndpointURL = fullApiServerUrl("")
                state.endpointSecret = accessToken
            }
        }
        catch(e) {
            log.debug "initializeAppEndpoint error ${e}"
            state.endpoint = null
        }
    }
    return state.endpoint
}
