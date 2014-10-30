package org.adaptlab.android.fit;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.adaptlab.android.logging.Log;
import org.adaptlab.android.logging.LogView;
import org.adaptlab.android.logging.LogWrapper;
import org.adaptlab.android.logging.MessageOnlyLogFilter;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessScopes;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.DataTypes;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Subscription;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataSourceListener;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.result.DataSourcesResult;
import com.google.android.gms.fitness.result.ListSubscriptionsResult;

public class ActivityMonitorActivity extends Activity {
	private static final String TAG = "ActivityMonitorActivity";
	private static final String AUTH_PENDING = "auth_state_pending";
    private static final int REQUEST_OAUTH = 1;
    private static final int REQUEST_BLUETOOTH = 1001;
    private static final int INTERVAL = 30;

	private boolean authInProgress = false;
    private GoogleApiClient mClient = null;
    private static DataSourceListener mLocationListener;
    private static DataSourceListener mHeartRateBpmListener;
    private static DataSourceListener mStepCountCumulativeListener;
    private static DataSourceListener mStepCountDeltaListener;
    private static DataSourceListener mDistanceCumulativeListener;
    private static DataSourceListener mDistanceDeltaListener;
    private static DataSourceListener mActivitySampleListener;

    //private BlueToothDevicesManager mBleDevicesManager;
    
    private static final DataType[] myDataTypes = { 
    	DataTypes.LOCATION_SAMPLE,
    	DataTypes.HEART_RATE_BPM, 
    	DataTypes.STEP_COUNT_CUMULATIVE, 
    	DataTypes.STEP_COUNT_DELTA,
    	DataTypes.DISTANCE_CUMULATIVE,
    	DataTypes.DISTANCE_DELTA,
    	DataTypes.ACTIVITY_SAMPLE 
    };
    
    private static final DataSourceListener[] mDataSourceListeners = {
    	mLocationListener,
    	mHeartRateBpmListener,
    	mStepCountCumulativeListener,
    	mStepCountDeltaListener,
    	mDistanceCumulativeListener,
    	mDistanceDeltaListener,
    	mActivitySampleListener
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_activity_monitor);
		initializeLogging();
		if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }
		buildFitnessClient();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_monitor, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(AUTH_PENDING, authInProgress);
    }
	
	@Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "Connecting...");
        mClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mClient.isConnected()) {
            mClient.disconnect();
        }
        unRegisterFitnessDataListeners();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	if (requestCode == REQUEST_OAUTH) {
            authInProgress = false;
            if (resultCode == RESULT_OK) {
                if (!mClient.isConnecting() && !mClient.isConnected()) {
                    mClient.connect();
                }
            }
        } else if (requestCode == REQUEST_BLUETOOTH) {
        	//mBleDevicesManager.startBleScan();
        }
    }

	private void buildFitnessClient() {
        mClient = new GoogleApiClient.Builder(this)
                
        		.addApi(Fitness.API)
                
                .addScope(FitnessScopes.SCOPE_ACTIVITY_READ)
                .addScope(FitnessScopes.SCOPE_LOCATION_READ)
                .addScope(FitnessScopes.SCOPE_BODY_READ)
                
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected!!!");
                                findFitnessDataSources();
                                subscribeIfNotAlreadySubscribed();
                                //mBleDevicesManager.startBleScan();
                            }

							@Override
                            public void onConnectionSuspended(int i) {
                                if (i == ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                
                .addOnConnectionFailedListener(
                        new GoogleApiClient.OnConnectionFailedListener() {
                            @Override
                            public void onConnectionFailed(ConnectionResult result) {
                                Log.i(TAG, "Connection failed. Cause: " + result.toString());
                                if (!result.hasResolution()) {
                                    GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), ActivityMonitorActivity.this, 0).show();
                                    return;
                                }
                                if (!authInProgress) {
                                    try {
                                        Log.i(TAG, "Attempting to resolve failed connection");
                                        authInProgress = true;
                                        result.startResolutionForResult(ActivityMonitorActivity.this, REQUEST_OAUTH);
                                    } catch (IntentSender.SendIntentException e) {
                                        Log.e(TAG, "Exception while starting resolution activity", e);
                                    }
                                }
                            }
                        }
                )
                
                .build();
        //mBleDevicesManager = new BlueToothDevicesManager(this, mClient);
    }
	
	private void findFitnessDataSources() {
		
		Fitness.SensorsApi.findDataSources(mClient, new DataSourcesRequest.Builder()
            //specify at least one data type    
        	.setDataTypes (myDataTypes)
                // Can specify whether data type is raw or derived.
                //.setDataSourceTypes(DataSource.TYPE_RAW)
                .build())
                .setResultCallback(new ResultCallback<DataSourcesResult>() {
                    @Override
                    public void onResult(DataSourcesResult dataSourcesResult) {
                        for (DataSource dataSource : dataSourcesResult.getDataSources()) {
                            Log.i(TAG, "Data source found: " + dataSource.getName());
                            
                            //Register listeners to receive Activity data!
                            if (dataSource.getDataType().equals(DataTypes.LOCATION_SAMPLE) && mLocationListener == null) {
                                createDataListener(mLocationListener, dataSource, DataTypes.LOCATION_SAMPLE);
                            } else if (dataSource.getDataType().equals(DataTypes.HEART_RATE_BPM) && mHeartRateBpmListener == null) {
                                createDataListener(mHeartRateBpmListener, dataSource, DataTypes.HEART_RATE_BPM);
                            } else if (dataSource.getDataType().equals(DataTypes.STEP_COUNT_CUMULATIVE) && mStepCountCumulativeListener == null) {
                                createDataListener(mStepCountCumulativeListener, dataSource, DataTypes.STEP_COUNT_CUMULATIVE);
                            } else if (dataSource.getDataType().equals(DataTypes.STEP_COUNT_DELTA) && mStepCountDeltaListener == null) {
                                createDataListener(mStepCountDeltaListener, dataSource, DataTypes.STEP_COUNT_DELTA);
                            } else if (dataSource.getDataType().equals(DataTypes.DISTANCE_CUMULATIVE) && mDistanceCumulativeListener == null) {
                                createDataListener(mDistanceCumulativeListener, dataSource, DataTypes.DISTANCE_CUMULATIVE);
                            } else if (dataSource.getDataType().equals(DataTypes.DISTANCE_DELTA) && mDistanceDeltaListener == null) {
                                createDataListener(mDistanceDeltaListener, dataSource, DataTypes.DISTANCE_DELTA);
                            } else if (dataSource.getDataType().equals(DataTypes.ACTIVITY_SAMPLE) && mActivitySampleListener == null) {
                                createDataListener(mActivitySampleListener, dataSource, DataTypes.ACTIVITY_SAMPLE);
                            }
                        }
                    }
        });
    }
	
	private void createDataListener(DataSourceListener listener, DataSource dataSource, DataType dataType) {
		listener = new DataSourceListener() {
            @Override
            public void onEvent(DataPoint dataPoint) {
                for (Field field : dataPoint.getDataType().getFields()) {
                    Value val = dataPoint.getValue(field);
                    Log.i(TAG, "Detected DataPoint field contents: " + field.describeContents());
                    Log.i(TAG, "Detected DataPoint field: " + field.getName());
                    Log.i(TAG, "Detected DataPoint value: " + val);
                    Log.i(TAG, "Detected DataPoint source: " + dataPoint.getDataSource());
                    Log.i(TAG, "Detected DataPoint original source: " + dataPoint.getOriginalDataSource());
                }
            }
        };
        registerDataListener(dataSource, dataType, listener);
	}

	private void registerDataListener(DataSource dataSource, DataType dataType, final DataSourceListener listener) {
		Fitness.SensorsApi.register(mClient,
                new SensorRequest.Builder()
                        .setDataSource(dataSource)
                        .setDataType(dataType) 
                        .setSamplingRate(INTERVAL, TimeUnit.SECONDS)
                        .build(),
                listener)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, listener.toString() + " listener registered! ");
                        } else {
                            Log.i(TAG, "Listener not registered. " + listener.toString());
                        }
                    }
        });
	}
	
	private void unRegisterFitnessDataListeners() {        
        for (DataSourceListener listener : mDataSourceListeners) {
        	if (listener != null) {
        		unRegisterListener(listener);
        	}
        }
    }
	
	private void unRegisterListener(final DataSourceListener listener) {
		Fitness.SensorsApi.unregister(mClient, listener)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, listener + " listener was removed!");
                        } else {
                            Log.i(TAG, listener + " listener was not removed.");
                        }
                    }
        });
	}
	
	private void subscribeIfNotAlreadySubscribed() {
		new Thread() {
            public void run() {
            	ListSubscriptionsResult subResults = getSubscriptionsList().await();
            	ArrayList<DataType> subscribedDataTypes = new ArrayList<DataType>();
            	
            	for (Subscription sc : subResults.getSubscriptions()) {
            		subscribedDataTypes.add(sc.getDataType());
            	}
            	
            	for (DataType dt : myDataTypes) {
            		if (!subscribedDataTypes.contains(dt)) {
            			Status status = Fitness.RecordingApi.subscribe(mClient, dt).await();
                        if (status.isSuccess()) {
                            Log.i(TAG, "Successfully subscribed to DataType: " + dt.toString());
                        } else {
                            Log.i(TAG, "There was a problem subscribing to DataType: " + dt.toString());
                        }
            		}
            	}  
            	
            }
        }.start();
	}
	
	private PendingResult<ListSubscriptionsResult> getSubscriptionsList() {
        return Fitness.RecordingApi.listSubscriptions(mClient);
    }
	
    public void cancelSubscription(Subscription sc) {
        final String dataTypeStr = sc.getDataType().toString();
        Log.i(TAG, "Unsubscribing from data type: " + dataTypeStr);

        Fitness.RecordingApi.unsubscribe(mClient, sc.getDataType())
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Successfully unsubscribed for data type: " + dataTypeStr);
                        } else {
                            Log.i(TAG, "Failed to unsubscribe for data type: " + dataTypeStr);
                        }
                    }
                });
    }
	
	// Using a custom log class that outputs both to in-app targets and logcat.
    public void initializeLogging() {
        // Wraps Android's native log framework.
        LogWrapper logWrapper = new LogWrapper();
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        Log.setLogNode(logWrapper);
        // Filter strips out everything except the message text.
        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);
        // On screen logging via a customized TextView.
        LogView logView = (LogView) findViewById(R.id.sample_logview);
        logView.setTextAppearance(this, R.style.Log);
        logView.setBackgroundColor(Color.WHITE);
        msgFilter.setNext(logView);
        Log.i(TAG, "Ready");
    }
    
}
