#include "BailuService.h"
#include "app-resources/resources.h"
#include "common/core/debug.h"
#include "meas_acc/resources.h"
#include "meas_temp/resources.h"
#include "whiteboard/builtinTypes/UnknownStructure.h"
#include "component_max3000x/resources.h"
#include "system_mode/resources.h"
#include "system_states/resources.h"
#include "ui_ind/resources.h"
#include "comm_ble/resources.h"
#include <float.h>
#include <math.h>
#include <vector>

#define ASSERT WB_DEBUG_ASSERT



// Also the led blinking period
#define TEMP_CHECK_TIME 10000
// Shut down after this
#define SHUTDOWN_TIME 30000

#define DEFAULT_RUNNING_TIME 60 // In minutes
#define MIN_ACC_SQUARED 2

#define PARTY_THRESHOLD 15
#define STAY_ON_SCORE 100

#define REFRESH_STATE_ID 2
#define REFRESH_PATH "/System/States/2"

int find(std::vector<Device> v, const char* s)
{
    for (int i = 0; i < v.size(); i++)
    {
        if (strcmp(v[i].address, s) == 0) return i;
    }
    return -1;
}

const char* const BailuService::LAUNCHABLE_NAME = "BailuService";

uint8_t s_customAvertiseData[] = {0x2,0x1,0x6,  // Block: Flags for BLE device
    0x8, 0xFF, 0xFE,0xFE,  0x0,0x0, 0x0, 0x0, 0x00            // Block: Data here is uint16_t for CompanyID, and five uint16_t for our data payload.
    };                  //Last byte determines the sensor to be advertising for this service when set 0xEA
const size_t s_dataPayloadIndex = sizeof(s_customAvertiseData) -5; // Points to second last byte

static const whiteboard::ExecutionContextId sExecutionContextId =
    WB_RES::LOCAL::FYSSA_BAILU::EXECUTION_CONTEXT;

static const whiteboard::LocalResourceId sProviderResources[] = {
    WB_RES::LOCAL::FYSSA_BAILU::LID,
    WB_RES::LOCAL::FYSSA_BAILU_STOP::LID,
};


BailuService::BailuService()
    : ResourceClient(WBDEBUG_NAME(__FUNCTION__), sExecutionContextId),
      ResourceProvider(WBDEBUG_NAME(__FUNCTION__), sExecutionContextId),
      LaunchableModule(LAUNCHABLE_NAME, sExecutionContextId),
      isRunning(true),
      secondAccAvr(0.0),
      minuteAccAvr(0.0),
      hourAccAvr(0.0),
      msCounter(0),
      currentTemp(0.0),
      tempThreshold(18),
      runningTime(DEFAULT_RUNNING_TIME),
      isPartying(false),
      prepareRun(false),
      isMeasuringAcc(false),
      isScanning(false),
      timePartying(0)
{

    mTimer = whiteboard::ID_INVALID_TIMER;

}

BailuService::~BailuService()
{
}

bool BailuService::initModule()
{
    if (registerProviderResources(sProviderResources) != whiteboard::HTTP_CODE_OK)
    {
        return false;
    }


    mModuleState = WB_RES::ModuleStateValues::INITIALIZED;
    return true;
}

void BailuService::deinitModule()
{
    unregisterProviderResources(sProviderResources);
    mModuleState = WB_RES::ModuleStateValues::UNINITIALIZED;
}

/** @see whiteboard::ILaunchableModule::startModule */
bool BailuService::startModule()
{
    mModuleState = WB_RES::ModuleStateValues::STARTED;
    mTimer = whiteboard::ResourceProvider::startTimer((size_t) TEMP_CHECK_TIME, true);
    listenRefreshes();
    return true;
}

void BailuService::stopModule()
{
    whiteboard::ResourceProvider::stopTimer(mTimer);
    mModuleState = WB_RES::ModuleStateValues::STOPPED;
    mTimer = whiteboard::ID_INVALID_TIMER;
    stopAcc();
}




void BailuService::onGetRequest(const whiteboard::Request& request,
                                      const whiteboard::ParameterList& parameters)
{
    DEBUGLOG("D/SENSOR/onGetRequest().");

    if (mModuleState != WB_RES::ModuleStateValues::STARTED)
    {
        return returnResult(request, wb::HTTP_CODE_SERVICE_UNAVAILABLE);
    }

    switch (request.getResourceConstId())
    {
    case WB_RES::LOCAL::FYSSA_BAILU_STOP::ID:
    {
        if (isMeasuringAcc) stopAcc();
        prepareRun = false;
        isRunning = false;
        timerCounter = 0;
        WB_RES::FyssaBailuResponse res;
        res.threshold = tempThreshold;
        res.seenDevices = mostDevices;
        res.time = (runningTime * 60000 - timerCounter) * 60000;
        res.curTemp = currentTemp;
        returnResult(request, whiteboard::HTTP_CODE_OK,
             ResponseOptions::Empty, res);
        break;
    }
    case WB_RES::LOCAL::FYSSA_BAILU::ID:
    {
        WB_RES::ScanParams pars;
        pars.active = false;
        pars.timeout = 60;
        pars.window = 6;
        pars.interval = 6;
        whiteboard::Result r = asyncSubscribe(WB_RES::LOCAL::COMM_BLE_SCAN(),AsyncRequestOptions::Empty, pars);
        DEBUGLOG("D/SENSOR/Result: %u", (uint32_t)r);
        WB_RES::FyssaBailuResponse res;
        res.threshold = tempThreshold;
        res.seenDevices = mostDevices;
        res.time = runningTime;
        res.curTemp = currentTemp;
        returnResult(request, whiteboard::HTTP_CODE_OK,
             ResponseOptions::Empty, res);
        break;
    }

  default:
        // Return error
        return returnResult(request, whiteboard::HTTP_CODE_NOT_IMPLEMENTED);
    }
}
void BailuService::onPutRequest(const whiteboard::Request& request,
                                      const whiteboard::ParameterList& parameters)
{
    DEBUGLOG("D/SENSOR/onPutRequest(). Requedt id: %d. Our config id: %d.", request.getResourceConstId(), WB_RES::LOCAL::FYSSA_BAILU::ID);

    if (mModuleState != WB_RES::ModuleStateValues::STARTED)
    {
        return returnResult(request, wb::HTTP_CODE_SERVICE_UNAVAILABLE);
    }

    switch (request.getResourceConstId())
    {
    case WB_RES::LOCAL::FYSSA_BAILU::ID:
    {

        if (isMeasuringAcc) stopAcc();
        stopScanning();
        // Parse and gather the specified settings
        auto config = WB_RES::LOCAL::FYSSA_BAILU::PUT::ParameterListRef(parameters).getFyssaBailuConfig();
        runningTime = config.time;
        tempThreshold = config.threshold;
        prepareRun = true;
        returnResult(request, whiteboard::HTTP_CODE_OK);
        break;
    }
  default:
        // Return error
        return returnResult(request, whiteboard::HTTP_CODE_NOT_IMPLEMENTED);
    }
}



void BailuService::startAcc(whiteboard::RequestId& remoteRequestId)
{
    if (isMeasuringAcc)
    {
        return;
    }

    DEBUGLOG("D/SENSOR/startAcc().");


    wb::Result result = asyncSubscribe(WB_RES::LOCAL::MEAS_ACC_SAMPLERATE::ID,
                            AsyncRequestOptions(&remoteRequestId, 0, true), ACC_SAMPLERATE);
    if (!wb::RETURN_OKC(result))
    {
        isRunning = false;
        isMeasuringAcc = false;
        return;
    }
    isMeasuringAcc = true;

    return;
}


void BailuService::stopAcc()
{
    if (!isMeasuringAcc)
    {
        return;
    }

    DEBUGLOG("D/SENSOR/stopAcc()");

    // Unsubscribe the LinearAcceleration resource, when unsubscribe is done, we get callback
    wb::Result result = asyncUnsubscribe(WB_RES::LOCAL::MEAS_ACC_SAMPLERATE::ID, NULL, ACC_SAMPLERATE);
    if (!wb::RETURN_OKC(result))
    {
        DEBUGLOG("D/SENSOR/asyncUnsubscribe threw error: %u", result);
    }
    isMeasuringAcc = false;
    return;
}

void BailuService::onAccData(whiteboard::ResourceId resourceId, const whiteboard::Value& value,
                                          const whiteboard::ParameterList& parameters)
{

    const WB_RES::AccData& accValues =
                value.convertTo<const WB_RES::AccData&>();

    if (accValues.arrayAcc.size() <= 0)
    {
        // No value, do nothing...
        return;
    }

    const whiteboard::Array<whiteboard::FloatVector3D>& arrayData = accValues.arrayAcc;
    uint32_t relativeTime = accValues.timestamp;
    for (size_t i = 0; i < arrayData.size(); i++)
    {
        whiteboard::FloatVector3D accValue = arrayData[i];
        double acc = sqrt(accValue.mX*accValue.mX + accValue.mY*accValue.mY + accValue.mZ*accValue.mZ) - 9.81;
        if (acc*acc < MIN_ACC_SQUARED) acc = 0;
        secondAccAvr = secondAccAvr*(ACC_SAMPLERATE-1)/ACC_SAMPLERATE + acc/ACC_SAMPLERATE;
        if (secondAccAvr > 10) secondAccAvr = 0;
        if (msCounter >= ACC_SAMPLERATE)
        {
            msCounter = 0;
            minuteAccAvr = minuteAccAvr*59/60 + secondAccAvr/60;
            if (minuteAccAvr > 7.0) minuteAccAvr = 0;
            hourAccAvr = (hourAccAvr*179 + minuteAccAvr)/180; // Looks nice on matlab, not a true average.
        }
        else ++msCounter;

    }
}

// This callback is called when the resource we have subscribed notifies us
void BailuService::onNotify(whiteboard::ResourceId resourceId, const whiteboard::Value& value,
                                          const whiteboard::ParameterList& parameters)
{
    DEBUGLOG("onNotify()");
    // Confirm that it is the correct resource
    switch (resourceId.getConstId())
    {
    case WB_RES::LOCAL::MEAS_ACC_SAMPLERATE::ID:
    {
        onAccData(resourceId, value, parameters);
        break;
    }
    case WB_RES::LOCAL::COMM_BLE_SCAN::ID:
    {
        auto res = value.convertTo<WB_RES::ScanResult>();
        if (!res.isScanResponse)
        {
            for (int i = 0; i < 7; i++) if (res.dataPacket[i] != s_customAvertiseData[i]) return; // Checking the advertisement to be indentical to the one of this app.
            if (res.dataPacket[11] == 0xEA) // Checking that the movesense sensor is indeed running this service.
            {
                int i = find(foundDevices, res.address);
                if (i == -1)
                {
                    Device d;
                    d.address = res.address;
                    d.timeAdded = timerCounter;
                    foundDevices.insert(foundDevices.end(), d);
                }
                else foundDevices[i] = {res.address, timerCounter};
            }
        }
        if (foundDevices.size() > mostDevices) mostDevices = foundDevices.size();
        removeOldScans();

        break;
    }
    case WB_RES::LOCAL::SYSTEM_STATES_STATEID::ID:
    {
        auto res = value.convertTo<WB_RES::StateChange>();
        if (res.stateId == REFRESH_STATE_ID)
        {
            WB_RES::State newState = res.newState;
            if (newState == 1) onRefresh();
        }
        break;
    }
    }
}

void BailuService::onGetResult(whiteboard::RequestId requestId, whiteboard::ResourceId resourceId, whiteboard::Result resultCode, const whiteboard::Value& rResultData)
{
    switch (resourceId.getConstId())
    {
    case WB_RES::LOCAL::MEAS_TEMP::ID:
    {
                // Temperature result or error
        if (resultCode == whiteboard::HTTP_CODE_OK)
        {
            WB_RES::TemperatureValue value = rResultData.convertTo<WB_RES::TemperatureValue>();
            float temperature = value.measurement;

            // Convert K to C
            temperature -= 273.15;
            currentTemp = temperature;
            DEBUGLOG("D/SENSOR/Temperature threshold: %u, current %u", (uint32_t)tempThreshold, (uint32_t)temperature);
            if (temperature >= tempThreshold)
            {
                isPartying = true;
                startAcc(mRemoteRequestId);
                startScanning();
            }
            else
            {
                isPartying = false;
                stopAcc();
                stopScanning();
            }
            advPartyScore();
        }
        break;
    }
    }
}


void BailuService::onPutResult(whiteboard::RequestId requestId, whiteboard::ResourceId resourceId, whiteboard::Result resultCode, const whiteboard::Value& rResultData)
{
    switch (resourceId.getConstId())
    {
    case WB_RES::LOCAL::COMM_BLE_ADV_SETTINGS::ID:
    {
        DEBUGLOG("COMM_BLE_ADV_SETTINGS returned status: %d", resultCode);
        break;
    }
    case WB_RES::LOCAL::COMM_BLE_SECURITY_SETTINGS::ID:
        if (resultCode == whiteboard::HTTP_CODE_OK) return;
        else shutDown();
        break;
    }

}


void BailuService::startScanning()
{
    if (isScanning) return;
    WB_RES::ScanParams pars;
    pars.active = false;
    pars.timeout = 0;
    pars.window = 0x0055;
    pars.interval = 0x1000;
    if (asyncSubscribe(WB_RES::LOCAL::COMM_BLE_SCAN(), AsyncRequestOptions::Empty, pars) == whiteboard::HTTP_CODE_OK)
    {
      isScanning = true;
      DEBUGLOG("Scanning");
    }
    else
    {
        DEBUGLOG("Failure in subscribing to scan");
    }
}

void BailuService::removeOldScans()
{
    int i = -1;
    while (++i < foundDevices.size())
    {
        if (foundDevices[i].timeAdded+60000 < timerCounter) foundDevices.erase(foundDevices.begin()+i);
    }
}

void BailuService::stopScanning()
{
    if (!isScanning) return;
    if (asyncUnsubscribe(WB_RES::LOCAL::COMM_BLE_SCAN(),NULL) ==  whiteboard::HTTP_CODE_OK) isScanning = false;
    foundDevices.clear();
}


void BailuService::checkPartyStatus()
{
    // Take temperature reading
    DEBUGLOG("D/SENSOR/checkPartyStatus()");
    asyncGet(WB_RES::LOCAL::MEAS_TEMP(), NULL);
}


double min(double a, double b)
{
    return (a < b) ? a : b;
}

float BailuService::calculateScoreFloat()
{
    double tempMult = (currentTemp-(double)tempThreshold)/(5.0+currentTemp-(double)tempThreshold);
    double minuteCeil = min(minuteAccAvr, 2.0);
    double hourCeil = min(hourAccAvr, 5.0);
    double minuteMult = (minuteCeil+3.0)*minuteCeil;
    double hourMult = 3*hourCeil;
    double timePartyingMult = 1.0 + sqrt((double)timePartying/60000);
    if (tempMult < 0) tempMult = 0;
    return (float)10*sqrt(tempMult*minuteMult*(foundDevices.size()+1)*hourMult*timePartyingMult);
}

uint32_t BailuService::calculateScore()
{
    uint32_t score = (uint32_t) calculateScoreFloat();
    if (score < PARTY_THRESHOLD) score = 0;
    return score;
}


void BailuService::advPartyScore()
{
    uint32_t score = calculateScore();
    if (!isPartying)
    {
      score = 0;
    }
    if (score == 0) timePartying = 0;


    // Update data to advertise packet
    s_customAvertiseData[s_dataPayloadIndex] = (uint8_t)((score & 0xFF00) >> 8);
    s_customAvertiseData[s_dataPayloadIndex+1] = (uint8_t)(score & 0xFF);


    s_customAvertiseData[s_dataPayloadIndex+2] = (uint8_t)((timePartying & 0xFF00) >> 8);
    s_customAvertiseData[s_dataPayloadIndex+3] = (uint8_t)(timePartying & 0xFF);
    s_customAvertiseData[s_dataPayloadIndex+4] = (uint8_t) 0xEA;
    // Update advertising packet
    WB_RES::AdvSettings advSettings;
    advSettings.interval = 6400; // 2000ms in 0.625ms BLE ticks
    advSettings.timeout = 0; // Advertise forever
    advSettings.advPacket = whiteboard::MakeArray<uint8>(s_customAvertiseData, sizeof(s_customAvertiseData));
    // NOTE: To modify scan response packet, just set similarily advSettings.scanRespPacket. Data format is the same
    // Here the scanRespPacket is left default so that the device is found with the usual name.
    asyncPut(WB_RES::LOCAL::COMM_BLE_ADV_SETTINGS(), AsyncRequestOptions::Empty, advSettings);
}

void BailuService::advNormal()
{

    s_customAvertiseData[s_dataPayloadIndex] = (uint8_t)(0);
    s_customAvertiseData[s_dataPayloadIndex+1] = (uint8_t)(0);
    s_customAvertiseData[s_dataPayloadIndex+2] = (uint8_t)(0);
    s_customAvertiseData[s_dataPayloadIndex+3] = (uint8_t)(0);
    s_customAvertiseData[s_dataPayloadIndex+4] = (uint8_t) 0x00;

    // Update advertising packet
    WB_RES::AdvSettings advSettings;
    advSettings.interval = 6400; // 2000ms in 0.625ms BLE ticks
    advSettings.timeout = 0; // Advertise forever
    advSettings.advPacket = whiteboard::MakeArray<uint8>(s_customAvertiseData, sizeof(s_customAvertiseData));
    // NOTE: To modify scan response packet, just set similarily advSettings.scanRespPacket. Data format is the same
    // Here the scanRespPacket is left default so that the device is found with the usual name.
    asyncPut(WB_RES::LOCAL::COMM_BLE_ADV_SETTINGS(), AsyncRequestOptions::Empty, advSettings);
}

void BailuService::onTimer(whiteboard::TimerId timerId)
{
    if (timerId == mTimer)
    {
        if (!isRunning)
        {
            timerCounter += TEMP_CHECK_TIME;
            if (timerCounter >= SHUTDOWN_TIME)
            {
                shutDown();
            }
        }
        else if (timerCounter >= runningTime*60000)
        {
            stopAcc();
            timerCounter = 0;
            advNormal();
            isRunning = false;
        }
        else
        {
            if (!isPartying) {
                minuteAccAvr = 0;
                hourAccAvr = 0;
                timePartying = 0;
            } else {
                if (score > STAY_ON_SCORE) timerCounter = 0;
                timePartying += TEMP_CHECK_TIME/1000;
            }
            // Make PUT request to trigger led blink
            asyncPut(WB_RES::LOCAL::UI_IND_VISUAL::ID, AsyncRequestOptions::Empty,(uint16_t) 2);
            timerCounter += TEMP_CHECK_TIME;
            checkPartyStatus();

        }

    }
}

void BailuService::shutDown()
{
    asyncPut(WB_RES::LOCAL::COMPONENT_MAX3000X_WAKEUP::ID,
                     AsyncRequestOptions(NULL, 0, true), (uint8_t)1);

            // Make PUT request to enter power off mode
    asyncPut(WB_RES::LOCAL::SYSTEM_MODE::ID,
                     AsyncRequestOptions(NULL, 0, true), // Force async
                     (uint8_t)1U);                       // WB_RES::SystemMode::FULLPOWEROFF
}

void BailuService::onClientUnavailable(whiteboard::ClientId clientId)
{
    DEBUGLOG("onClientUnavailable()");
    /*if (prepareRun) {
      prepareRun = false;
      isRunning = true;
    }*/
}
void BailuService::onRemoteWhiteboardDisconnected(whiteboard::WhiteboardId whiteboardId)
{
    DEBUGLOG("onRemoteWhiteboardDisconnected");
    if (prepareRun) {
      prepareRun = false;
      isRunning = true;
      timerCounter = 0;
      advPartyScore();
    }
}


void BailuService::listenRefreshes()
{
    whiteboard::ResourceId resId;
    wb::Result result = getResource(REFRESH_PATH, resId);
    if (!wb::RETURN_OKC(result))
    {
        return;
    }
    result = asyncSubscribe(resId, AsyncRequestOptions::Empty);
    if (result == whiteboard::HTTP_CODE_OK) {
      DEBUGLOG("Listening to taps");
    }
    else DEBUGLOG("Error listening to taps. Code %u", (uint8_t) result);
}

void BailuService::onRefresh()
{
    // Make PUT request to trigger led blink
    asyncPut(WB_RES::LOCAL::UI_IND_VISUAL::ID, AsyncRequestOptions::Empty,(uint16_t) 2);
    timerCounter = 0;
    if (!isRunning)
    {
    isRunning = true;
    advPartyScore();
    }
}

