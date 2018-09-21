#include "BailuService.h"
#include "app-resources/resources.h"
#include "common/core/debug.h"
#include "meas_acc/resources.h"
#include "meas_temp/resources.h"
#include "whiteboard/builtinTypes/UnknownStructure.h"
#include "whiteboard/integration/bsp/shared/debug.h"
#include "component_max3000x/resources.h"
#include "system_mode/resources.h"
#include "ui_ind/resources.h"
#include "comm_ble/resources.h"
#include <float.h>
#include <math.h>

#define ASSERT WB_DEBUG_ASSERT


// Also the led blinking period
#define TEMP_CHECK_TIME 5000
// Shut down after this 
#define SHUTDOWN_TIME 120000

const char* const BailuService::LAUNCHABLE_NAME = "BailuService";

uint8_t s_customAvertiseData[] = {0x2,0x1,0x6,  // Block: Flags for BLE device 
    0x7, 0xFF, 0xFE,0xFE,  0x0,0x0, 0x0, 0x0              // Block: Manuf specific data for embedding our custom data. Data here is uint16_t for CompanyID, and two uint16_t for our data payload
    };
const size_t s_dataPayloadIndex = sizeof(s_customAvertiseData) -4; // Points to second last byte

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
      isRunning(false),
      secondAccAvr(0.0),
      minuteAccAvr(0.0),
      msCounter(0),
      currentTemp(0.0),
      tempThreshold(0),
      runningTime(0),
      isPartying(false),
      prepareRun(false),
      isMeasuringAcc(false)
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
        WB_RES::FyssaBailuResponse res;
        res.threshold = tempThreshold;
        res.time = runningTime;
        res.curTemp = currentTemp;
        returnResult(request, whiteboard::HTTP_CODE_OK,
             ResponseOptions::Empty, res);
        break;
    }
    case WB_RES::LOCAL::FYSSA_BAILU::ID:
    {
        WB_RES::FyssaBailuResponse res;
        res.threshold = tempThreshold;
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
        secondAccAvr = secondAccAvr*(ACC_SAMPLERATE-1)/ACC_SAMPLERATE + acc/ACC_SAMPLERATE;
        if (msCounter >= ACC_SAMPLERATE)
        {
            msCounter = 0;
            minuteAccAvr = minuteAccAvr*59/60 + secondAccAvr/60;
        }
        else ++msCounter;
        
    }
}

// This callback is called when the resource we have subscribed notifies us
void BailuService::onNotify(whiteboard::ResourceId resourceId, const whiteboard::Value& value,
                                          const whiteboard::ParameterList& parameters)
{
    // Confirm that it is the correct resource
    switch (resourceId.getConstId())
    {
    case WB_RES::LOCAL::MEAS_ACC_SAMPLERATE::ID:
    {
        onAccData(resourceId, value, parameters);
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
            }
            else
            {
                isPartying = false;
                stopAcc();
            }
            advPartyScore();
        }
        break;
    }
    
    }
}


void BailuService::onPutResult(whiteboard::RequestId requestId, whiteboard::ResourceId resourceId, whiteboard::Result resultCode, const whiteboard::Value& rResultData)
{
    if (resourceId.localResourceId == WB_RES::LOCAL::COMM_BLE_ADV_SETTINGS::LID)
    {
        DEBUGLOG("COMM_BLE_ADV_SETTINGS returned status: %d", resultCode);
    }
}




void BailuService::checkPartyStatus()
{
    // Take temperature reading
    DEBUGLOG("D/SENSOR/checkPartyStatus()");
    asyncGet(WB_RES::LOCAL::MEAS_TEMP(), NULL);
}


void BailuService::advPartyScore()
{
    
    uint16_t score = (uint16_t) ((currentTemp-tempThreshold)*minuteAccAvr);
    if (!isPartying) score = 0;
    // Update data to advertise packet
    s_customAvertiseData[s_dataPayloadIndex] = (uint8_t)(score >> 8);
    s_customAvertiseData[s_dataPayloadIndex+1] = (uint8_t)(score & 0xFF);

    uint16_t timePartying = (uint16_t)timerCounter/60000;
    DEBUGLOG("D/SENSOR/getPartyScore() with values %u %u", score, timePartying);
        s_customAvertiseData[s_dataPayloadIndex+2] = (uint8_t)(timePartying >> 8);
    s_customAvertiseData[s_dataPayloadIndex+3] = (uint8_t)(timePartying & 0xFF);

    // Update advertising packet
    WB_RES::AdvSettings advSettings;
    advSettings.interval = 1600; // 1000ms in 0.625ms BLE ticks
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
            asyncPut(WB_RES::LOCAL::COMPONENT_MAX3000X_WAKEUP::ID,
                     AsyncRequestOptions(NULL, 0, true), (uint8_t)1);

            // Make PUT request to enter power off mode
            asyncPut(WB_RES::LOCAL::SYSTEM_MODE::ID,
                     AsyncRequestOptions(NULL, 0, true), // Force async
                     (uint8_t)1U);                       // WB_RES::SystemMode::FULLPOWEROFF
            }
        }
        else if (timerCounter >= runningTime*60000) 
        {
            stopAcc();
            timerCounter = 0;
            isRunning = false;
        }
        else
        {
            if (!isPartying) minuteAccAvr = 0;
            // Make PUT request to trigger led blink
            asyncPut(WB_RES::LOCAL::UI_IND_VISUAL::ID, AsyncRequestOptions::Empty,(uint16_t) 2);
            timerCounter += TEMP_CHECK_TIME;
            checkPartyStatus();
            
        }

    }
}
void BailuService::onClientUnavailable(whiteboard::ClientId clientId)
{
    DEBUGLOG("onClientUnavailable()");
    if (prepareRun) {
      prepareRun = false;
      isRunning = true;
    }
}
void BailuService::onRemoteWhiteboardDisconnected(whiteboard::WhiteboardId whiteboardId)
{
    DEBUGLOG("onRemoteWhiteboardDisconnected");
    if (prepareRun) {
      prepareRun = false;
      isRunning = true;
    }
}




