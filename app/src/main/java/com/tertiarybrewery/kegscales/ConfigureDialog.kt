package com.tertiarybrewery.kegscales



import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment


class ConfigureDialog: DialogFragment(), KegScaleConnector.ConfigListener, View.OnClickListener {
    companion object {

        const val TAG = "configure_dialog"
        fun open(kegId : String, kegNumber: Int, kegOrder: KegOrder, kegScaleConnector: KegScaleConnector): ConfigureDialog {
            val f = ConfigureDialog()
            f.kegId = kegId
            f.kegScaleConnector = kegScaleConnector
            f.kegNumber = kegNumber
            f.kegOrder = kegOrder
            return f
        }
    }


    private lateinit var kegId : String
    private var kegNumber : Int = 0
    private lateinit var kegOrder : KegOrder
    private lateinit var kegScaleConnector: KegScaleConnector
    private lateinit var toolbar: Toolbar
    var dialogListener: configureDialogListener? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun setListener(l: configureDialogListener) {
        this.dialogListener = l
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        kegScaleConnector.setConfigListener(this)

        val view = inflater.inflate(R.layout.configure_dialog, container, false)
        toolbar = view.findViewById((R.id.toolbar))
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setNavigationOnClickListener { v: View? -> dismiss() }
        toolbar.title = "Configure Keg"
        toolbar.inflateMenu(R.menu.configure_menu)
        toolbar.setOnMenuItemClickListener { item: MenuItem? ->
            dismiss()
            true
        }
        Log.i("Configure Dialog", "Keg - $kegId")
    }
    override fun onStart() {
        super.onStart()
        var dialog: Dialog? = getDialog();
        if (dialog != null) {
            dialog.getWindow()?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            kegScaleConnector.requestConfigStatus(kegId)
            var volumeBox: EditText?= dialog?.findViewById(R.id.keg_volume)
            if (volumeBox != null) {
                val volume = kegScaleConnector.getVolume(kegId)
                volumeBox.setText(volume.toString())
            }
            val volButton: Button? = dialog?.findViewById(R.id.update_volume)
            volButton?.setOnClickListener(this)
            var nameBox: EditText?= dialog?.findViewById(R.id.keg_name)
            if (nameBox != null) {
                val name = kegScaleConnector.getName(kegId)
                nameBox.setText(name.toString())
            }
            val nameButton: Button? = dialog?.findViewById(R.id.update_name)
            nameButton?.setOnClickListener(this)
            
            var leftButton: Button?= dialog?.findViewById(R.id.left)
            leftButton?.setOnClickListener(this)
            var rightButton: Button? = dialog?.findViewById(R.id.right)
            rightButton?.setOnClickListener(this)
            if (kegNumber == 0) {
                disableButton(leftButton)
            }
            if (kegNumber == kegOrder.size()-1) {
                disableButton(rightButton)
            }
        }
    }

    private fun disableButton(button: Button?) {
        button?.isEnabled = false
        button?.setTextColor(ContextCompat.getColor(requireView().context, R.color.white))
        button?.setBackgroundColor(ContextCompat.getColor(requireView().context, R.color.inactive))
    }

    override fun onConfigStatus(kegAddress: String, configStatus: Int) {
        Log.v("Config Status", "Status $configStatus")
        activity?.runOnUiThread {
            val instructions: TextView? = dialog?.findViewById(R.id.instructions)
            val layout: ConstraintLayout? = dialog?.findViewById(R.id.configDialog)
            val button: Button? = dialog?.findViewById(R.id.config_update)
            var buttonText: String = "RESET"
            var messageText: String = "Reset Calibration"

            if (configStatus == 0) {
                buttonText="Set Empty"
                messageText = "Place EMPTY keg on the scales, leave for 5 minutes and then press the button to begin calibration"
            } else if (configStatus==1) {
                buttonText="Set Full"
                messageText = "Place FULL keg on the scales, leave for 5 minutes and then press the button to complete calibration"
            }
            if (instructions != null) {
                instructions.text = messageText
            }
            if (button != null) {
                button.text = buttonText
                button.setOnClickListener(this)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onClick(v: View?) {
        when (v?.getId()) {
            R.id.config_update -> {
                onUpdateConfigClick(v)
            }
            R.id.update_volume -> {
                onUpdateVolumeClick(v)
            }
            R.id.update_name -> {
                onUpdateNameClick(v)
            }
            R.id.left -> {
                Log.i("Movebutton", "left")
                kegOrder.moveKegLeft(kegNumber)
            }
            R.id.right -> {
                Log.i("Movebutton", "right")
                kegOrder.moveKegRight(kegNumber)
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onUpdateNameClick(v: View) {
        val name: EditText? = dialog?.findViewById(R.id.keg_name)
        Log.i("test", "flicked " + (name?.text))
        val nameString = name?.text.toString()
        if (name != null) {
            kegScaleConnector.setName(kegId, nameString)
        }
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onUpdateConfigClick(v: View) {
        kegScaleConnector.triggerConfig(kegId)
        Log.v("Set Empty", "test")
        Thread.sleep(300)
        kegScaleConnector.requestConfigStatus(kegId)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun onUpdateVolumeClick(view: View?) {
        val volume: EditText? = dialog?.findViewById(R.id.keg_volume)
        Log.i("test", "flicked " + (volume?.text))
        val volumeInt = volume?.text.toString().toInt()
        if (volume != null) {
            kegScaleConnector.setVolume(kegId, volumeInt )
        }
    }

    override fun onDetach() {
        super.onDetach()
        dialogListener?.onClose()
    }

    interface configureDialogListener {
        fun onClose()
    }
}