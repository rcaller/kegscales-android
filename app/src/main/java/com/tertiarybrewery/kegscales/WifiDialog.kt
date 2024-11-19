package com.tertiarybrewery.kegscales

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment

class WifiDialog: DialogFragment(), View.OnClickListener {
    companion object {

        const val TAG = "wifi_dialog"
        fun open(
            kegId: String,
            kegNumber: Int,
            kegOrder: KegOrder,
            kegScaleConnector: KegScaleConnector
        ): WifiDialog {
            val f = WifiDialog()
            f.kegId = kegId
            f.kegScaleConnector = kegScaleConnector
            f.kegNumber = kegNumber
            f.kegOrder = kegOrder
            return f
        }
    }

    private lateinit var kegId: String
    private var kegNumber: Int = 0
    private lateinit var kegOrder: KegOrder
    private lateinit var kegScaleConnector: KegScaleConnector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view = inflater.inflate(R.layout.wifi_dialog, container, false)
        return view
    }
    override fun onStart() {
        super.onStart()
        var dialog: Dialog? = getDialog();
        if (dialog != null)
        {
            dialog.getWindow()?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
            );
            val setButton: Button? = dialog?.findViewById(R.id.setwifi)
            setButton?.setOnClickListener(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onClick(v: View?) {
        when (v?.getId()) {
            R.id.setwifi -> {
                onSetWifiClick(v)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onSetWifiClick(v: View) {
        val ssid: EditText? = dialog?.findViewById(R.id.ssid)
        val pwd: EditText? = dialog?.findViewById(R.id.password)
        val ssidString = ssid?.text.toString()
        val pwdString = pwd?.text.toString()
        if (ssidString != null && pwdString!=null) {
            kegScaleConnector.setWifi(kegId, ssidString, pwdString)
        }
        dialog?.dismiss()
    }
}
