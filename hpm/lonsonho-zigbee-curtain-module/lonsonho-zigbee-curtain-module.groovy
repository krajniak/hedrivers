/**
 *
 *	Copyright 2019 Andrzej Krajniak
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 */

import groovy.json.JsonOutput
import hubitat.zigbee.zcl.DataType

metadata {
	definition(name: "Lonsonho ZigBee Curtain Module", namespace: "hubitat", author: "Andrzej Krajniak") {
		capability "Actuator"
		capability "Configuration"
		capability "Refresh"
		capability "WindowShade"
		capability "HealthCheck"

		command "pause"
        command "swapButtons"
        command "startCalibration"
        command "stopCalibration"

        attribute "lift", "number"
        attribute "tilt", "number"
        attribute "position", "number"
        attribute "windowShade", "enum", ["opening", "partially open", "closed", "open", "closing", "unknown"]
        attribute "buttonsSwapped", "enum", ["normal", "swapped"]

        attribute "tuyaMovingStateRaw", "number"
        attribute "tuyaMotorReversalRaw", "number"
        attribute "tuyaCalibrationRaw", "number"


        fingerprint profileId: "0104", inClusters: "0004, 0005, 0006, 0102, 0000", outClusters: "0019", manufacturer: "_TZ3000_fccpjz5z", model: "TS130F", deviceJoinName: "Curtain Module" // Lonsonho QS-Zigbee-C01
	}

	preferences {
        input "preset", "number", title: "Preset position", description: "Set the window shade preset position", defaultValue: 50, range: "1..100", required: false, displayDuringSetup: false
	}


}

private getCLUSTER_WINDOW_COVERING() { 0x0102 }
private getCOMMAND_OPEN() { 0x00 }
private getCOMMAND_CLOSE() { 0x01 }
private getCOMMAND_PAUSE() { 0x02 }
private getCOMMAND_GOTO_LIFT_PERCENTAGE() { 0x05 }
private getCOMMAND_GOTO_TILT_PERCENTAGE() { 0x08 }
private getATTRIBUTE_POSITION_LIFT() { 0x0008 }
private getATTRIBUTE_POSITION_TILT() { 0x0009 }
private getATTRIBUTE_TUYA_MOVING_STATE() { 0xF000 }
private getATTRIBUTE_TUYA_CALIBRATION() { 0xF001 }
private getATTRIBUTE_MOTOR_REVERSAL() { 0xF002 }



private List<Map> collectAttributes(Map descMap) {
	List<Map> descMaps = new ArrayList<Map>()

	descMaps.add(descMap)

	if (descMap.additionalAttrs) {
		descMaps.addAll(descMap.additionalAttrs)
	}

	return descMaps
}

// Parse incoming device messages to generate events
def parse(String description) {
	// log.debug "description:- ${description}"

	// if (device.currentValue("shadeLevel") == null && device.currentValue("level") != null) {
	// 	sendEvent(name: "shadeLevel", value: device.currentValue("level"), unit: "%")
	// }

	if (description?.startsWith("read attr -")) {
	    // log.debug "description:- ${description}"
		Map descMap = zigbee.parseDescriptionAsMap(description)

		if (descMap?.clusterInt == CLUSTER_WINDOW_COVERING && descMap.value) {
			// log.debug "attr: ${descMap?.attrInt}, value: ${descMap?.value}, descValue: ${Integer.parseInt(descMap.value, 16)}, ${device.getDataValue("model")}"
			List<Map> descMaps = collectAttributes(descMap)
			def tiltmap = descMaps.find { it.attrInt == ATTRIBUTE_POSITION_TILT }
			def liftmap = descMaps.find { it.attrInt == ATTRIBUTE_POSITION_LIFT }
			def movemap = descMaps.find { it.attrInt == ATTRIBUTE_TUYA_MOVING_STATE }
			def calimap = descMaps.find { it.attrInt == ATTRIBUTE_TUYA_CALIBRATION }
			def mrevmap = descMaps.find { it.attrInt == ATTRIBUTE_MOTOR_REVERSAL }


			if (tiltmap && tiltmap.value) {
				def newLevel = zigbee.convertHexToInt(tiltmap.value)
				tiltEventHandler(newLevel)
			}

            if (liftmap && liftmap.value) {
				def newLevel = zigbee.convertHexToInt(liftmap.value)
				liftEventHandler(newLevel)
			}

            if (movemap && movemap.value) {
				def mstate = zigbee.convertHexToInt(movemap.value)
				movingStateEventHandler(mstate)
			}

            if (calimap && calimap.value) {
				def calstate = zigbee.convertHexToInt(calimap.value)
				calibrationEventHandler(calstate)
			}

            if (mrevmap && mrevmap.value) {
				def motorstate = zigbee.convertHexToInt(mrevmap.value)
				motorReversalEventHandler(motorstate)
			}
		} 
	}
}

def tiltEventHandler(currentTilt) {
    // log.debug "handling tilt"
    if (tilt != currentTilt){
        sendEvent(name: "tilt", value: currentTilt, unit:"%")
    }
}

def liftEventHandler(currentLift) {
    // log.debug "handling lift"
    if (lift != currentLift){
        sendEvent(name: "lift", value: currentLift, unit:"%")
        sendEvent(name: "position", value: currentLift, unit:"%")
    }
}

def movingStateEventHandler(currentMovingState){
    // log.debug "handling moving state change"
    if (tuyaMovingStateRaw != currentMovingState){
        sendEvent(name: "tuyaMovingStateRaw", value: currentMovingState)
    }
}

def motorReversalEventHandler(currentMotorReversal){
    // log.debug "handling motor reversal change"
    if (tuyaMotorReversalRaw != currentMotorReversal){
        sendEvent(name: "tuyaMotorReversalRaw", value: currentMotorReversal)
    }
}

def calibrationEventHandler(currentCalibration){
    // log.debug "handling calibration change"
    if (tuyaCalibrationRaw != currentCalibration){
        sendEvent(name: "tuyaCalibrationRaw", value: currentCalibration)
    }
}

def levelEventHandler(currentLevel) {
	//log.debug "levelEventHandle - currentLevel: ${currentLevel} lastLevel: ${lastLevel}"

	if ((lastLevel == "undefined" || currentLevel == lastLevel) && state.invalidSameLevelEvent) { //Ignore invalid reports
		log.debug "Ignore invalid reports"
	} else {
		state.invalidSameLevelEvent = true

		// sendEvent(name: "shadeLevel", value: currentLevel, unit: "%")
		// sendEvent(name: "level", value: currentLevel, unit: "%", displayed: false)
        sendEvent(name: "postion", value: level, unit:"%")


		if (currentLevel == 0 || currentLevel == 100) {
			sendEvent(name: "windowShade", value: currentLevel == 0 ? "closed" : "open")
		} else {
			if (lastLevel < currentLevel) {
				sendEvent([name:"windowShade", value: "opening"])
			} else if (lastLevel > currentLevel) {
				sendEvent([name:"windowShade", value: "closing"])
			}
			runIn(1, "updateFinalState", [overwrite:true])
		}
	}
}

def updateFinalState() {
	def level = device.currentValue("shadeLevel")
	//log.debug "updateFinalState: ${level}"

	if (level > 0 && level < 100) {
		sendEvent(name: "windowShade", value: "partially open")
	}
}

def close() {
	//log.info "close()"
	zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_CLOSE)
}

def open() {
	//log.info "open()"
	zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_OPEN)
}

def setPosition(value) {
	//log.info "setPosition($value)"

	setShadeLevel(value)
}

def setShadeLevel(value) {
	//log.info "setShadeLevel($value)"

	Integer level = Math.max(Math.min(value as Integer, 100), 0)
    
    // sendEvent(name:"level", value: level, unit:"%")
    // sendEvent(name:"shadeLevel", value: level, unit:"%")    
    // sendEvent(name:"postion", value: level, unit:"%")
	def cmd

	cmd = zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_GOTO_LIFT_PERCENTAGE, zigbee.convertToHexString(level, 2))

	return cmd
}

def pause() {
	//log.info "pause()"
	def currentShadeStatus = device.currentValue("windowShade")

	if (currentShadeStatus == "open" || currentShadeStatus == "closed") {
		sendEvent(name: "windowShade", value: currentShadeStatus)
	} else {
		zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_PAUSE)
	}
}

def presetPosition() {
	setShadeLevel(preset ?: 50)
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	return refresh()
}

def refresh() {
	//log.info "refresh()"
	def cmds

	cmds = zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT)
	cmds += zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_TILT)
	cmds += zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_TUYA_CALIBRATION)
	cmds += zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_TUYA_MOVING_STATE)
	cmds += zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_MOTOR_REVERSAL)
	
	// return cmds
}

def configure() {
	def cmds

	//log.info "configure()"

	//log.debug "Configuring Reporting and Bindings."

	cmds = zigbee.configureReporting(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_TILT, DataType.UINT8, 0, 60, null)
	cmds += zigbee.configureReporting(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT, DataType.UINT8, 0, 60, null)
	cmds += zigbee.configureReporting(CLUSTER_WINDOW_COVERING, ATTRIBUTE_TUYA_CALIBRATION, DataType.UINT8, 0, 60, null)
	cmds += zigbee.configureReporting(CLUSTER_WINDOW_COVERING, ATTRIBUTE_TUYA_MOVING_STATE, DataType.UINT8, 0, 60, null)
	cmds += zigbee.configureReporting(CLUSTER_WINDOW_COVERING, ATTRIBUTE_MOTOR_REVERSAL, DataType.UINT8, 0, 60, null)

	return refresh() + cmds
}

def swapButtons() {
    tuyaMotorReversal = device.currentValue("tuyaMotorReversalRaw")
	// log.debug "tuyaMotorReversal:- ${tuyaMotorReversal}"
    
    Integer newValue
    newValue = 1 - tuyaMotorReversal
	// log.debug "newValue:- ${newValue}"
    zigbee.writeAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_MOTOR_REVERSAL, 0x30, newValue)
}

def startCalibration() {
    tuyaCalibration = device.currentValue("tuyaCalibrationRaw")
    if (tuyaCalibration == 1) {
        zigbee.writeAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_TUYA_CALIBRATION, 0x30, 0)
    }
}

def stopCalibration() {
    tuyaCalibration = device.currentValue("tuyaCalibrationRaw")
    if (tuyaCalibration == 0) {
        zigbee.writeAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_TUYA_CALIBRATION, 0x30, 1)
    }
}

// private def parseBindingTableMessage(description) {
// 	Integer groupAddr = getGroupAddrFromBindingTable(description)
// 	if (groupAddr) {
// 		List cmds = addHubToGroup(groupAddr)
// 		cmds?.collect { new hubitat.device.HubAction(it) }
// 	}
// }

// private Integer getGroupAddrFromBindingTable(description) {
// 	//log.info "Parsing binding table - '$description'"
// 	def btr = zigbee.parseBindingTableResponse(description)
// 	def groupEntry = btr?.table_entries?.find { it.dstAddrMode == 1 }

// 	//log.info "Found ${groupEntry}"

// 	!groupEntry?.dstAddr ?: Integer.parseInt(groupEntry.dstAddr, 16)
// }

// private List addHubToGroup(Integer groupAddr) {
// 	["st cmd 0x0000 0x01 ${CLUSTER_GROUPS} 0x00 {${zigbee.swapEndianHex(zigbee.convertToHexString(groupAddr,4))} 00}", "delay 200"]
// }

// private List readDeviceBindingTable() {
// 	["zdo mgmt-bind 0x${device.deviceNetworkId} 0", "delay 200"]
// }
