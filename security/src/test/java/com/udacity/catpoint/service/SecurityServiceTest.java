package com.udacity.catpoint.service;

import com.udacity.catpoint.ImageService;
import com.udacity.catpoint.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.function.Executable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.udacity.catpoint.data.ArmingStatus.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    @Mock
    ImageService imageService;

    @Mock
    SecurityRepository securityRepository;

    @Mock
    SecurityService securityService;

    private Sensor sensor;

    static int runningNumber = 1;

    @BeforeEach
    /**
     * Set a test case with a random sensor type
     */
    void setUp() {
        securityService = new SecurityService(securityRepository, imageService);
        SensorType sensorType = getARandomSensorType();
        sensor = createSensor();
    }

    /**
     * Get a random sensor type
     * @return Random sensor type
     */
    private SensorType getARandomSensorType() {
        int number = ThreadLocalRandom.current().nextInt(SensorType.class.getEnumConstants().length);
        return SensorType.class.getEnumConstants()[number];
    }

    /**
     * Create sensors
     * @param number Number of sensors to be created
     * @param active Should the sensor to be activated?
     * @return List of sensors created
     */
    private Set<Sensor> createSensors(int number, boolean active) {
        Set<Sensor> sensorSet = new HashSet<>();

        for (int i = 1; i <= number; i++) {
            sensorSet.add(createSensor());
        }

        sensorSet.forEach(sensor -> sensor.setActive(active));

        return sensorSet;
    }

    /**
     * Create a sensor from a random sensor type
     * @return A new sensor
     */
    private Sensor createSensor() {
        SensorType sensorType = getARandomSensorType();
        return new Sensor(sensorType.name() + "_" + runningNumber++, sensorType);
    }

    /**
     * Test 1
     * Change the alarm status to pending alarm when arming status is armed and sensors are activated.
     */
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    public void changeAlarmStatus_armingStatusArmedAndSensorActivated_pendingAlarm(ArmingStatus armingStatus) {
        when(securityService.getArmingStatus()).thenReturn(armingStatus);
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    /**
     * Test 2
     * Deactivate the alarm when armed, sensors activated and pending alarm
     */
    @Test
    public void changeAlarmStatus_alarmArmedAndSensorActivatedAndPendingAlarm_deactivateAlarm() {
        when(this.securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, atLeastOnce()).setAlarmStatus(AlarmStatus.ALARM);
    }

    /**
     * Test 3
     * Deactivate the alarm when sensors inactivated and pending alarm
     */
    @Test
    public void changeAlarmStatus_pendingAlarmAndAllSensorsInactive_deactivateAlarm() {
        Set<Sensor> sensors = createSensors(3, true);
        when(this.securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensors.forEach(sensor -> securityService.changeSensorActivationStatus(sensor, false));
        sensors.forEach(sensor -> verify(securityRepository, atLeastOnce()).setAlarmStatus(AlarmStatus.NO_ALARM));
    }

    /**
     * Test 4
     * Do not change the alarm status when the alarm is active and the sensor status is changed
     */
    @Test
    public void noChangeInAlarmStatus_activeAlarmAndChangeSensorState_noChange() {
        lenient().when(securityRepository.getArmingStatus()).thenReturn(ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        Set<Sensor> sensors = createSensors(2, true);
        Sensor aSensor = sensors.iterator().next();
        securityService.changeSensorActivationStatus(aSensor, false);
        assertEquals(AlarmStatus.ALARM, securityRepository.getAlarmStatus());
    }

    /**
     * Test 5
     * Activate the alarm when sensors are activated while they are active and the system is in a pending alarm status.
     */
    @Test
    public void changeAlarmState_activatedSensorWhileActiveAndSystemPending_activateAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, atMostOnce()).setAlarmStatus(AlarmStatus.ALARM);
    }

    /**
     * Test 6
     * Do not change the alarm status when sensors are inactive and are inactive
     */
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"PENDING_ALARM", "NO_ALARM", "ALARM"})
    public void noChangeInAlarmState_deactivatedSensorWhileInactive_noChangeinAlarmState(AlarmStatus alarmStatus) {
        when(securityService.getAlarmStatus()).thenReturn(alarmStatus);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, atMostOnce()).getAlarmStatus();
        assertEquals(alarmStatus, securityRepository.getAlarmStatus());
    }

    /**
     * Test 7
     * Activate the alarm when the camera identifies a cat and the system is in state "ARMED_HOME"
     */
    @Test
    public void changeAlarmStatus_catImageIdentifiedAndSystemArmedHome_activateAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ARMED_HOME);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        verify(securityRepository, atMostOnce()).setAlarmStatus(AlarmStatus.ALARM);
    }

    /**
     * Test 8
     * Deactivate the alarm when camera does not detect a cat while sensors are inactive
     */
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_AWAY", "ARMED_HOME"})
    public void changeAlarmStatus_catImageNotIdentifiedAndSensorInactive_deactivateAlarm(ArmingStatus armingStatus) {
        lenient().when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        Set<Sensor> sensors = createSensors(3, false);
        lenient().when(securityRepository.getSensors()).thenReturn(sensors);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));
        verify(securityRepository, atMostOnce()).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /**
     * Test 9
     * Deactivate the alarm when the system is disarmed
     */
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"PENDING_ALARM", "ALARM"})
    public void changeAlarmStatus_systemDisarmed_deactivateAlarm(AlarmStatus alarmStatus) {
        lenient().when(securityRepository.getAlarmStatus()).thenReturn(alarmStatus);
        lenient().when(securityRepository.getArmingStatus()).thenReturn(DISARMED);
        verify(securityRepository, atMostOnce()).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /**
     * Test 10
     * Deactivate sensors when the system is armed
     */
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_AWAY", "ARMED_HOME"})
    public void changeSensorsStates_systemArmed_deactivateSensors(ArmingStatus armingStatus) {
        lenient().when(securityRepository.getArmingStatus()).thenReturn(DISARMED);
        Set<Sensor> sensors = createSensors(2, true);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getSensors()).thenReturn(sensors);
        securityService.setArmingStatus(armingStatus);
        sensors.forEach(sensor -> assertEquals(false, sensor.getActive()));
    }

    /**
     * Test 11
     * Activate the alarm when the system is "ARMED_HOME" ans the camera identifies a cat
     */
    @Test
    public void changeAlarmStatus_systemArmedHomeAndCatImageIdentified_activateAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(DISARMED);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        lenient().when(securityRepository.getArmingStatus()).thenReturn(ARMED_HOME);
        verify(securityRepository, atMostOnce()).setAlarmStatus(AlarmStatus.ALARM);
    }
}
