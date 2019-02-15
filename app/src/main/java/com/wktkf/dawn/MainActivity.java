/*
 *  Copyright 2016 Lipi C.H. Lee
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package com.wktkf.dawn;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.CompoundButton;
import android.widget.Switch;

public class MainActivity extends AppCompatActivity {
	Switch mProxyStart;
	private static final int VPN_REQUEST_CODE = 0x0F;

	/*
        private boolean waitingForVPNStart;
        private BroadcastReceiver vpnStateReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                if (LocalVPNService.BROADCAST_VPN_STATE.equals(intent.getAction()))
                {
                    if (intent.getBooleanExtra("running", false))
                        waitingForVPNStart = false;
                }
            }
        };*/
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mProxyStart = (Switch) findViewById(R.id.startProxy);


		if(isVpnRunning()){
			mProxyStart.setChecked(true);
		}
		mProxyStart.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if(isChecked) {
					startVPN();
				} else{
					stopVPN();
				}
			}
		});

        /*waitingForVPNStart = false;
        LocalBroadcastManager.getInstance(this).registerReceiver(vpnStateReceiver,
                new IntentFilter(LocalVPNService.BROADCAST_VPN_STATE));*/
	}
	private void startVPN() {
		Intent vpnIntent = VpnService.prepare(this);
		if (vpnIntent != null)
			startActivityForResult(vpnIntent, VPN_REQUEST_CODE); //Prepare to establish a VPN connection. This method returns null if the VPN application is already prepared or if the user has previously consented to the VPN application. Otherwise, it returns an Intent to a system activity.
		else
			onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
	}
	private void stopVPN() {
		Intent intent = new Intent("stop_kill");
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}


	@Override
	protected void onResume() {
		super.onResume();

		if(isVpnRunning()){
			mProxyStart.setChecked(true);
		} else {
			mProxyStart.setChecked(false);

		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
			Intent intent = new Intent(this, DawnVPNService.class);

			// waitingForVPNStart = true;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				//startForegroundService(intent);
				startService(intent);
			} else {
				startService(intent);
			}
		}
	}

	private boolean isVpnRunning() {
		ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
		return cm.getNetworkInfo(ConnectivityManager.TYPE_VPN).isConnectedOrConnecting();
	}

}
