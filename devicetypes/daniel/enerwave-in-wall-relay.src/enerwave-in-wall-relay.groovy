/**
 *  Copyright 2015 SmartThings
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

	definition (name: "Enerwave In-Wall Relay", namespace: "Daniel", author: "Daniel Ionescu") {
		capability "Actuator"
		capability "Switch"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Relay Switch"
        capability "Configuration"

		fingerprint deviceId: "0x1001", inClusters: "0x5E,0x20,0x25,0x27,0x70,0x72,0x85,0x86"
		//fingerprint deviceId: "0x1003", inClusters: "0x25,0x2B,0x2C,0x27,0x75,0x73,0x70,0x86,0x72"
    	//fingerprint deviceId: "0x1001", inClusters: "0x20,0x25,0x27,0x72,0x86,0x70,0x85"
		fingerprint deviceId: "0x1003", inClusters: "0x20,0x25,0x27,0x5E,0x60,0x70,0x72,0x85,0x86,0x8E2"
       
	}

	// simulator metadata
	simulator {
		status "on":  "command: 2003, payload: FF"
		status "off": "command: 2003, payload: 00"

		// reply messages
		reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
		reply "200100,delay 100,2502": "command: 2503, payload: 00"
	}

	// tile definitions
tiles(scale: 2) {
		standardTile("switch", "device.switch", width: 6, height: 4, canChangeIcon: true) {
			state "on", label: '${name}', action: "switch.off", icon: "st.switches.light.on", backgroundColor: "#79b821"
			state "off", label: '${name}', action: "switch.on", icon: "st.switches.light.off", backgroundColor: "#ffffff"
		}
		standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        standardTile("configure", "device.switch",  width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
		state "default", label:"", action:"configure", icon:"st.secondary.configure"
    }
        standardTile("blank", "device.switch",  width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
		state "default", label:"", action:"", icon:"st.switches.light.on"
    }
		main "switch"
		details(["switch","configure","blank","refresh"])
	}
}

//Parameter is permanent. Not needed after first run

def configure() {
	log.debug "configure() called"
    def cmds = []
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 3, configurationValue: [1]).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 3).format()
    cmds
}

def updated()
{
	log.debug "Preferences have been changed. Attempting configure()"
    def cmds = configure()
    response(cmds)
}
    
def installed() {
	zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
}

def parse(String description) {
	def result = null
	def cmd = zwave.parse(description, [0x20: 1, 0x70: 1])
	if (cmd) {
		result = createEvent(zwaveEvent(cmd))
	}
	if (result?.name == 'hail' && hubFirmwareLessThan("000.011.00602")) {
		result = [result, response(zwave.basicV1.basicGet())]
		log.debug "Was hailed: requesting state update"
	} else {
		log.debug "Parse returned ${result?.descriptionText}"
	}
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	[name: "switch", value: cmd.value ? "on" : "off", type: "physical"]
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	[name: "switch", value: cmd.value ? "on" : "off", type: "digital"]
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
	def value = "when off"
	if (cmd.configurationValue[0] == 1) {value = "when on"}
	if (cmd.configurationValue[0] == 2) {value = "never"}
	[name: "indicatorStatus", value: value, display: false]
}

def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
	[name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false]
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	if (state.manufacturer != cmd.manufacturerName) {
		updateDataValue("manufacturer", cmd.manufacturerName)
	}

	final relays = [
	    [manufacturerId:0x0113, productTypeId: 0x5246, productId: 0x3133, productName: "Evolve LFM-20"],
        [manufacturerId:0x0113, productTypeId: 0x5246, productId: 0x3133, productName: "Linear FS20Z-1"],
		[manufacturerId:0x5254, productTypeId: 0x8000, productId: 0x0002, productName: "Remotec ZFM-80"],
        [manufacturerId:0x011A, productTypeId: 0x0101, productId: 0x5605, productName: "Enerwave ZWN-RSM1"]
	]

	def productName  = null
	for (it in relays) {
		if (it.manufacturerId == cmd.manufacturerId && it.productTypeId == cmd.productTypeId && it.productId == cmd.productId) {
			productName = it.productName
			break
		}
	}

	if (productName) {
		log.debug "Relay found: $productName"
		updateDataValue("productName", productName)
	}
	else {
		log.debug "Not a relay, retyping to Z-Wave Switch"
		setDeviceType("Z-Wave Switch")
	}
	[name: "manufacturer", value: cmd.manufacturerName]
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
	[:]
}

def on() {
	delayBetween([
		zwave.basicV1.basicSet(value: 0xFF).format(),
		zwave.switchBinaryV1.switchBinaryGet().format()
	])
}

def off() {
	delayBetween([
		zwave.basicV1.basicSet(value: 0x00).format(),
		zwave.switchBinaryV1.switchBinaryGet().format()
	])
}

def poll() {
	zwave.switchBinaryV1.switchBinaryGet().format()
}

def refresh() {
	delayBetween([
		zwave.switchBinaryV1.switchBinaryGet().format(),
		zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	])
}
