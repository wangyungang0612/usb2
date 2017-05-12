package com.won.usb_ch340;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

@SuppressLint("HandlerLeak")
public class MainActivity extends Activity {
	private final String TAG = MainActivity.class.getSimpleName();
	// view
	private TextView mTextView;
	private Button mButton, mClearButton;
	private CheckBox mCheckBox;

	private CH340AndroidDriver ch340AndroidDriver;
	private final int baurt = 9600;

	private static final String ACTION_USB_PERMISSION = "com.wondfo.USB_PERMISSION";

	private boolean isExits = false;
	private boolean isGet = false;

	private final int BUF_SIZE = 64;// 接收缓冲区大小
	private final int LENGTH = 64;// 读取的字节数，默认设置为 64
									// 字节，用户也可以根据需要修改此值；返回实际读取的字节数。

	private final int MSG_USB_INSERT = 0xAA;
	private final int MSG_USB_UNINSERT = 0xBB;
	private final int MSG_USB_GETDATA = 0xCC;

	Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
			case MSG_USB_GETDATA:
				if (isGet)
					mTextView.setText(mTextView.getText().toString() + "--\n"
							+ bytes2HexString((byte[]) msg.obj));
				break;
			case MSG_USB_INSERT:
				initUSB();
				mCheckBox.setChecked(true);
				break;
			case MSG_USB_UNINSERT:
				ch340AndroidDriver.CloseDevice();
				mCheckBox.setChecked(false);
				break;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initView();
		registerReceiver(mUsbDeviceReceiver, new IntentFilter(
				UsbManager.ACTION_USB_DEVICE_ATTACHED));
		registerReceiver(mUsbDeviceReceiver, new IntentFilter(
				UsbManager.ACTION_USB_DEVICE_DETACHED));
		ch340AndroidDriver = new CH340AndroidDriver(
				(UsbManager) getSystemService(Context.USB_SERVICE), this,
				ACTION_USB_PERMISSION);
		Intent i = getIntent();
		String action = i.getAction();
		if (action.equals("android.hardware.usb.action.USB_DEVICE_ATTACHED")) {
			initUSB();
			mCheckBox.setChecked(true);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		isExits = true;
		ch340AndroidDriver.CloseDevice();
		unregisterReceiver(mUsbDeviceReceiver);
	}

	private void initUSB() {
		UsbDevice device = ch340AndroidDriver.EnumerateDevice();// 枚举设备，获取USB设备
		ch340AndroidDriver.OpenDevice(device);// 打开并连接USB
		if (ch340AndroidDriver.isConnected()) {
			boolean flags = ch340AndroidDriver.UartInit();// 初始化串口
			if (!flags) {
				Log.e(TAG, "Init Uart Error");
				Toast.makeText(MainActivity.this, "Init Uart Error",
						Toast.LENGTH_SHORT).show();
			} else {// 配置串口
				if (ch340AndroidDriver.SetConfig(baurt, (byte) 8, (byte) 1,
						(byte) 0, (byte) 0)) {
					Log.e(TAG, "Uart Configed");
				}
			}
		} else {
			Log.e(TAG, "ch340AndroidDriver not connected");
		}

		new Thread() {
			@Override
			public void run() {
				super.run();
				while (!isExits) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (ch340AndroidDriver != null) {
						byte[] readBuffer = new byte[BUF_SIZE];
						if (ch340AndroidDriver.ReadData(readBuffer, LENGTH) > 0) {
							Message msg = new Message();
							msg.what = MSG_USB_GETDATA;
							msg.obj = readBuffer;
							L.e("11111"+bytes2HexString(readBuffer));
							handler.sendMessage(msg);
							isGet = true;
							Log.e(TAG, "read success:"
									+ bytes2HexString(readBuffer));
						} else {
							isGet = false;
						}
					}
				}
			}
		}.start();
	}

	// [start] bytes2HexString byte与16进制字符串的互相转换
	public String bytes2HexString(byte[] b) {
		String ret = "";
		for (int i = 0; i < b.length; i++) {
			String hex = Integer.toHexString(b[i] & 0xFF);
			if (hex.length() == 1) {
				hex = '0' + hex;
			}
			ret += hex.toUpperCase();
		}
		return ret;
	}

	private void initView() {
		mTextView = (TextView) findViewById(R.id.txtReciver);
		mButton = (Button) findViewById(R.id.btnSend);
		mClearButton = (Button) findViewById(R.id.btnClear);
		mCheckBox = (CheckBox) findViewById(R.id.cbUSBStatue);
		mButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				byte[] message = new byte[4];
				message[0] = 0x1A;
				message[1] = 0x2A;
				message[2] = 0x3A;
				message[3] = 0x4B;
				try {
					ch340AndroidDriver.WriteData(message, message.length);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		mClearButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mTextView.setText("");
			}
		});
	}

	private final BroadcastReceiver mUsbDeviceReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Toast.makeText(MainActivity.this, action, Toast.LENGTH_LONG).show();
			Log.e(TAG, "action:" + action);
			if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				UsbDevice deviceFound = (UsbDevice) intent
						.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				Toast.makeText(
						MainActivity.this,
						"ACTION_USB_DEVICE_ATTACHED: \n"
								+ deviceFound.toString(), Toast.LENGTH_LONG)
						.show();
				handler.sendEmptyMessage(MSG_USB_INSERT);
			} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
				UsbDevice device = (UsbDevice) intent
						.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				Toast.makeText(MainActivity.this,
						"ACTION_USB_DEVICE_DETACHED: \n" + device.toString(),
						Toast.LENGTH_LONG).show();
				handler.sendEmptyMessage(MSG_USB_UNINSERT);
			}
		}

	};

}
