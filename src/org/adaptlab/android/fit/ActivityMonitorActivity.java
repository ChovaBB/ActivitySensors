package org.adaptlab.android.fit;

import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.util.Log;
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
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.BleDevice;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.DataTypes;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.BleScanCallback;
import com.google.android.gms.fitness.request.DataSourceListener;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.request.StartBleScanRequest;
import com.google.android.gms.fitness.result.DataSourcesResult;

public class ActivityMonitorActivity extends Activity {
	private static final String TAG = "ActivityMonitorActivity";
	private static final String AUTH_PENDING = "auth_state_pending";
    private static final int REQUEST_OAUTH = 1;
    private static final int REQUEST_BLUETOOTH = 1001;
    
	private boolean authInProgress = false;
    private GoogleApiClient mClient = null;
    private DataSourceListener mLocationListener;
    private DataSourceListener mHeartRateBpmListener;
    private DataSourceListener mStepCountCumulativeListener;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_activity_monitor);
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
        	startBleScan();
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
    }
	
	private void findFitnessDataSources() {
		startBleScan();
		
		Fitness.SensorsApi.findDataSources(mClient, new DataSourcesRequest.Builder()
            //specify at least one data type    
        	.setDataTypes
                (
                        //DataTypes.ACTIVITY_SAMPLE,
                        DataTypes.LOCATION_SAMPLE,
                        DataTypes.HEART_RATE_BPM,
                        DataTypes.STEP_COUNT_CUMULATIVE
                        //DataTypes.CALORIES_EXPENDED
        		)
                // Can specify whether data type is raw or derived.
                //.setDataSourceTypes(DataSource.TYPE_RAW)
                .build())
                .setResultCallback(new ResultCallback<DataSourcesResult>() {
                    @Override
                    public void onResult(DataSourcesResult dataSourcesResult) {
                        Log.i(TAG, "Result: " + dataSourcesResult.getStatus().toString());
                        for (DataSource dataSource : dataSourcesResult.getDataSources()) {
                            Log.i(TAG, "Data source found: " + dataSource.toString());
                            Log.i(TAG, "Data Source type: " + dataSource.getDataType().getName());

                            //Register listeners to receive Activity data!
                            if (dataSource.getDataType().equals(DataTypes.LOCATION_SAMPLE) && mLocationListener == null) {
                                Log.i(TAG, "Data source for LOCATION found!  Registering.");
                                createLocationDataListener(dataSource, DataTypes.LOCATION_SAMPLE);
                            } else if (dataSource.getDataType().equals(DataTypes.HEART_RATE_BPM) && mHeartRateBpmListener == null) {
                                Log.i(TAG, "Data source for HEART RATE BPM found!  Registering.");
                                createHeartRateDataListener(dataSource, DataTypes.HEART_RATE_BPM);
                            } else if (dataSource.getDataType().equals(DataTypes.STEP_COUNT_CUMULATIVE) && mStepCountCumulativeListener == null) {
                                Log.i(TAG, "Data source for STEP_COUNT_CUMULATIVE found!  Registering.");
                                createStepCountDataListener(dataSource, DataTypes.STEP_COUNT_CUMULATIVE);
                            } 
                        }
                    }
        });
    }
	
	private void createLocationDataListener(DataSource dataSource, DataType dataType) {
		mLocationListener = new DataSourceListener() {
            @Override
            public void onEvent(DataPoint dataPoint) {
                for (Field field : dataPoint.getDataType().getFields()) {
                    Value val = dataPoint.getValue(field);
                    Log.i(TAG, "Detected DataPoint field: " + field.getName());
                    Log.i(TAG, "Detected DataPoint value: " + val);
                }
            }
        };
        registerDataListener(dataSource, dataType, mLocationListener);
    }
	
	private void createHeartRateDataListener(DataSource dataSource, DataType dataType) {
		mHeartRateBpmListener = new DataSourceListener() {
            @Override
            public void onEvent(DataPoint dataPoint) {
                for (Field field : dataPoint.getDataType().getFields()) {
                    Value val = dataPoint.getValue(field);
                    Log.i(TAG, "Detected DataPoint field: " + field.getName());
                    Log.i(TAG, "Detected DataPoint value: " + val);
                }
            }
        };
        registerDataListener(dataSource, dataType, mHeartRateBpmListener);
    }
	
	private void createStepCountDataListener(DataSource dataSource, DataType dataType) {
		mStepCountCumulativeListener = new DataSourceListener() {
            @Override
            public void onEvent(DataPoint dataPoint) {
                for (Field field : dataPoint.getDataType().getFields()) {
                    Value val = dataPoint.getValue(field);
                    Log.i(TAG, "Detected DataPoint field: " + field.getName());
                    Log.i(TAG, "Detected DataPoint value: " + val);
                }
            }
        };
        registerDataListener(dataSource, dataType, mStepCountCumulativeListener);
	}

	private void registerDataListener(DataSource dataSource, DataType dataType, final DataSourceListener listener) {
		Fitness.SensorsApi.register(mClient,
                new SensorRequest.Builder()
                        .setDataSource(dataSource)
                        .setDataType(dataType) 
                        .setSamplingRate(1, TimeUnit.MINUTES)
                        .build(),
                listener)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Listener registered! " + listener.toString());
                        } else {
                            Log.i(TAG, "Listener not registered. " + listener.toString());
                        }
                    }
        });
	}
	
	private void startBleScan() {
        
		BleScanCallback callback = new BleScanCallback() {
            @Override
			public void onDeviceFound(BleDevice device) {
            	Log.i(TAG, "device found!");
            	Log.i(TAG, device.getName());
            	//claimDevice(device);
            }
            @Override
            public void onScanStopped() {
            	Log.i(TAG, "scan stopped");
            }
        };
		
		StartBleScanRequest request = new StartBleScanRequest.Builder()
        	.setDataTypes(DataTypes.HEART_RATE_BPM)
        	.setBleScanCallback(callback)
        	.build();  
		
        PendingResult<Status> result = Fitness.BleApi.startBleScan(mClient, request);
        result.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                if (!status.isSuccess()) {
                	switch (status.getStatusCode()) {
                    case FitnessStatusCodes.DISABLED_BLUETOOTH:
                        try {
                            status.startResolutionForResult(ActivityMonitorActivity.this, REQUEST_BLUETOOTH);
                        } catch (SendIntentException e) {
                            //
                        }
                        break;
                    //
                }
            	Log.i(TAG, "BLE scan unsuccessful");
                } else {
                	Log.i(TAG, "ble scan status message: " + status.getStatusMessage());
                	Log.i(TAG, "BLE scan successful");
                }
            }
        });
    }
	
    public void claimDevice(BleDevice device) {
		PendingResult<Status> pendingResult = Fitness.BleApi.claimBleDevice(mClient, device);
		pendingResult.setResultCallback(new ResultCallback<Status>() {
			@Override
			public void onResult(Status st) {
				if (st.isSuccess()) {
					Log.i(TAG, "Claimed device successfully");
				} else {
					Log.e(TAG, "Did not successfully claim device");
				}
			}
		});
	}
    
}
