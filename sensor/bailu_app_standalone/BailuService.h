#pragma once


#include <whiteboard/LaunchableModule.h>
#include <whiteboard/ResourceClient.h>
#include <whiteboard/ResourceProvider.h>
#include <whiteboard/containers/RequestMap.h>

#include "wb-resources/resources.h"
#include <string>
#include <vector>

#define ASSERT WB_DEBUG_ASSERT



// Also the led blinking period
#define TEMP_CHECK_TIME 10000
// Shut down after this
#define SHUTDOWN_TIME 30000

#define DEFAULT_RUNNING_TIME 15 // In minutes
#define MIN_ACC_SQUARED 2

#define PARTY_THRESHOLD 15
#define STAY_ON_SCORE 100

#define REFRESH_STATE_ID 2
#define REFRESH_PATH "/System/States/2"
#define ACC_SAMPLERATE 13

typedef struct {
    const char* address;
    uint32_t timeAdded;
} Device;

class BailuService FINAL : private whiteboard::ResourceClient,
                                         private whiteboard::ResourceProvider,
                                         public whiteboard::LaunchableModule

{
public:
    /** Name of this class. Used in StartupProvider list. */
    static const char* const LAUNCHABLE_NAME;
    BailuService();
    ~BailuService();

private:
    /** @see whiteboard::ILaunchableModule::initModule */
    virtual bool initModule() OVERRIDE;

    /** @see whiteboard::ILaunchableModule::deinitModule */
    virtual void deinitModule() OVERRIDE;

    /** @see whiteboard::ILaunchableModule::startModule */
    virtual bool startModule() OVERRIDE;

    /** @see whiteboard::ILaunchableModule::stopModule */
    virtual void stopModule() OVERRIDE;

    /**
    *   GET POST and DELETE request handlers.
    *
    *   @param requestId ID of the request
    *   @param clientId ID of the client that should receive the result
    *   @param resourceId ID of the associated resource
    *   @param parameters List of parameters for the request
    *   @return Result of the operation
    */
    virtual void onGetRequest(const whiteboard::Request& request,
                              const whiteboard::ParameterList& parameters) OVERRIDE;

    virtual void onPutRequest(const whiteboard::Request& request,
                              const whiteboard::ParameterList& parameters) OVERRIDE;



    /**
    *	Callback for resource notifications.
    *   Note that this function will not be called for notifications that are
    *   of types WB_RESOURCE_NOTIFICATION_TYPE_INSERT or WB_RESOURCE_NOTIFICATION_TYPE_DELETE,
    *   just for notifications that are of type WB_RESOURCE_NOTIFICATION_TYPE_UPDATE.
    *
    *	@param resourceId Resource id associated with the update
    *	@param rValue Current value of the resource
    */
    virtual void onNotify(whiteboard::ResourceId resourceId, const whiteboard::Value& value,
                          const whiteboard::ParameterList& parameters);

        /**
    *	Callback for asynchronous resource GET requests
    *
    *	@param requestId ID of the request
    *	@param resourceId Successful request contains ID of the resource
    *	@param resultCode Result code of the request
    *	@param rResultData Successful result contains the request result
    */
    virtual void onGetResult(whiteboard::RequestId requestId, whiteboard::ResourceId resourceId, whiteboard::Result resultCode, const whiteboard::Value& rResultData);
    virtual void onPutResult(whiteboard::RequestId requestId, whiteboard::ResourceId resourceId, whiteboard::Result resultCode, const whiteboard::Value& rResultData);


/**
    * Local client 'disconnect' notification handler.
    *
    *  This can be used for example to cleanup possible subscription related information of the client.
    *
    *  @see whiteboard::ResourceProvider::onSubscribe
    *  @see whiteboard::ResourceProvider::onUnsubscribe
    */
    virtual void onClientUnavailable(whiteboard::ClientId clientId) OVERRIDE;
    /**
    *  Whiteboard disconnect notification handler.
    *
    *  This can be used for example to cleanup possible subscription related information of clients from
    *  the remote whiteboard.
    *
    *  @param whiteboardId ID of the whiteboard that has been disconnected.
    *
    *  @see whiteboard::ResourceProvider::onSubscribe
    *  @see whiteboard::ResourceProvider::onUnsubscribe
    */
    virtual void onRemoteWhiteboardDisconnected(whiteboard::WhiteboardId whiteboardId) OVERRIDE;

protected:
    /**
    *	Timer callback.
    *
    *	@param timerId Id of timer that triggered
    */
    virtual void onTimer(whiteboard::TimerId timerId) OVERRIDE;

private:
    void listenRefreshes();
    void onRefresh();

    void startAcc(whiteboard::RequestId& remoteRequestId);
    void stopAcc();

    void startScanning();
    void stopScanning();

    std::vector<Device> foundDevices;
    uint32_t mostDevices = 0;
    void removeOldScans();

    void shutDown();

    void onAccData(whiteboard::ResourceId resourceId, const whiteboard::Value& value,
                                          const whiteboard::ParameterList& parameters);

    void checkPartyStatus();

    float calculateScoreFloat();
    uint32_t calculateScore();

    bool isPartying;
    float currentTemp;

    void advPartyScore();
    void advNormal();

    whiteboard::RequestId mRemoteRequestId;

    bool prepareRun;
    bool isRunning;
    bool isMeasuringAcc;
    bool isScanning;

    uint32_t runningTime;
    uint32_t tempThreshold;

    double secondAccAvr;
    double minuteAccAvr;
    double hourAccAvr;
    uint16_t msCounter;
    uint16_t score = 0;

    whiteboard::TimerId mTimer;


    uint32_t timerCounter;
    uint16_t timePartying;
};
