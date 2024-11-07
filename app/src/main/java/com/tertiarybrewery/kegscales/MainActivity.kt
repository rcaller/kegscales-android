package com.tertiarybrewery.kegscales


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Timer
import java.util.TimerTask


class MainActivity : AppCompatActivity(), KegScaleConnector.BleListener, OnRequestPermissionsResultCallback,
    ConfigureDialog.configureDialogListener {
    private var kegScaleConnector: KegScaleConnector = KegScaleConnector(this)
    private lateinit var mImageView: ImageView
    private lateinit var bitmap: Bitmap
    private lateinit var canvas: Canvas
    private lateinit var paint: Paint
    private lateinit var kegSetDrawingData: KegSetDrawingData


    private lateinit var kegOrder: KegOrder

    private var dw: Int = 0
    private var dh: Int = 0
    private var titleHeight: Float = 0.0f
    private var textHeight : Float = 0.0F
    private var textWidth : Float = 0.0F



    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val preferences = getPreferences(Context.MODE_PRIVATE)
        kegOrder = KegOrder(preferences)
        setContentView(R.layout.activity_main)
        mImageView = findViewById(R.id.kegImageView)


        titleHeight = spToPx(20F).toFloat()
        textHeight = spToPx(15F).toFloat()
        textWidth = textHeight*7

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        kegScaleConnector.setListener(this)

        checkAndRequestBluetoothPermissions()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(!::canvas.isInitialized) {
            dh = mImageView.height
            dw = mImageView.width - 50
            Log.i("Screen", "width: " + dw.toString())
            // Creating a bitmap with fetched dimensions
            bitmap = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)

            // Storing the canvas on the bitmap
            canvas = Canvas(bitmap)
            canvas.drawColor(ResourcesCompat.getColor(getResources(), R.color.colorPrimary, theme))
            paint = Paint()
            paint.setTypeface(Typeface.DEFAULT_BOLD)
        }
    }

    override fun onDestroy() {
        kegScaleConnector.disconnectAll()
        super.onDestroy()

    }

    override fun onResume() {
        super.onResume()
  //      mImageView = findViewById(R.id.kegImageView)
        mImageView.invalidate();
    }



    fun onClickScan(v: View?) {
        Log.w("MainActvity", "Start Scan")
        if (v?.id == R.id.scan) {
            kegScaleConnector.startBleScan()
            val pgsBar: ProgressBar = findViewById(R.id.spinner)
            pgsBar.visibility = View.VISIBLE
        }
    }

    fun initKegSetDrawingData(kegCount: Int) {
        kegSetDrawingData = KegSetDrawingData(kegCount, dw, dh)
        mImageView.setImageBitmap(bitmap)
        drawKegs()

    }

    private fun drawKegs() {
        canvas.drawColor(ResourcesCompat.getColor(getResources(), R.color.colorPrimary, theme), PorterDuff.Mode.MULTIPLY)
        Log.i("Kegorder", kegOrder.size().toString())
        for (keg in 0 .. (kegOrder.size()-1)) {
            drawKeg(keg)
            setName(keg)
        }
    }

    fun drawKeg(kegNumber: Int) {
        val kegX = kegSetDrawingData.kegs[kegNumber].x

        paint.color = Color.BLACK
        paint.strokeWidth = 10F
        canvas.drawRect(((kegX-5).toFloat()), 20F, ((kegX+kegSetDrawingData.kegWidth+5).toFloat()), ((kegSetDrawingData.kegHeight+5).toFloat()), paint)
        paint.color = Color.LTGRAY
        canvas.drawRect(((kegX).toFloat()), 25F, ((kegX+kegSetDrawingData.kegWidth).toFloat()), ((kegSetDrawingData.kegHeight).toFloat()), paint)
    }

    fun drawFillLevel(kegNumber: Int, fillPercentage: Int) {
        if (::kegSetDrawingData.isInitialized) {
            val kegTextX = kegSetDrawingData.kegs[kegNumber].textX
            val kegTextY = titleHeight + (textHeight*2)
            paint.color =ResourcesCompat.getColor(getResources(), R.color.colorPrimary, theme)
            canvas.drawRect(kegTextX.toFloat(), kegTextY+textHeight, kegTextX.toFloat()+textWidth, kegTextY-textHeight, paint)
            paint.color = Color.DKGRAY
            paint.setTextSize(textHeight)
            canvas.drawText("Remaining", kegTextX.toFloat(), kegTextY.toFloat(), paint)
            canvas.drawText("$fillPercentage %", kegTextX.toFloat(), (kegTextY+textHeight).toFloat(), paint)
          
            Log.i("drawFillLevel", "Drawing " + kegNumber.toString())
            val kegX = kegSetDrawingData.kegs[kegNumber].x
            paint.color = Color.LTGRAY
            canvas.drawRect(
                ((kegX).toFloat()),
                25F,
                ((kegX + kegSetDrawingData.kegWidth).toFloat()),
                ((kegSetDrawingData.kegHeight).toFloat()),
                paint
            )
            paint.color = ResourcesCompat.getColor(getResources(), R.color.colorBeer, theme)
            canvas.drawRect(
                ((kegX).toFloat()),
                ((kegSetDrawingData.kegHeight - (fillPercentage * kegSetDrawingData.percentHeight))),
                ((kegX + kegSetDrawingData.kegWidth).toFloat()),
                ((kegSetDrawingData.kegHeight).toFloat()),
                paint
            )
            mImageView.invalidate();
        }
    }

    private fun setLastPour(kegNumber: Int, poured: Int) {
        Log.i("setLastPour", "Called")
        if (::kegSetDrawingData.isInitialized) {
            Log.i("setLastPour", "Pouring $poured")

            val kegTextX = kegSetDrawingData.kegs[kegNumber].textX
            val kegTextY = kegSetDrawingData.kegHeight - 150
            paint.color =ResourcesCompat.getColor(getResources(), R.color.colorPrimary, theme)
            canvas.drawRect(kegTextX.toFloat(), kegTextY.toFloat(), kegTextX.toFloat()+textWidth, (kegTextY+textHeight), paint)
            paint.color = Color.DKGRAY
            paint.setTextSize(textHeight)
            canvas.drawText("Last Pour", kegTextX.toFloat(), kegTextY.toFloat(), paint)
            canvas.drawText("$poured ml", kegTextX.toFloat(), (kegTextY+textHeight), paint)
            Timer().schedule(object : TimerTask(){
                override fun run() {
                    paint.color = ResourcesCompat.getColor(getResources(), R.color.colorPrimary, theme)
                    canvas.drawRect(
                        kegTextX.toFloat(),
                        (kegTextY-textHeight).toFloat(),
                        kegTextX.toFloat() + 350F,
                        (kegTextY + textHeight).toFloat(),
                        paint
                    )
                }

            }, 10000)


        }
    }
    private fun setName(kegNumber: Int) {
        if (::kegSetDrawingData.isInitialized) {

            val nameX = kegSetDrawingData.kegs[kegNumber].textX
            val nameY = titleHeight
            paint.color = ResourcesCompat.getColor(getResources(), R.color.colorPrimary, theme)
            canvas.drawRect(
                nameX.toFloat(),
                nameY,
                nameX.toFloat() + textWidth,
                (nameY - titleHeight).toFloat(),
                paint
            )
            paint.color = Color.DKGRAY
            paint.setTextSize(titleHeight)
            val kegId = kegOrder.getKegIdInPosition(kegNumber)
            Log.v("Get Name", "keg $kegId : " +kegScaleConnector.getKegScales(kegId)?.name)

            val name = kegScaleConnector.getKegScales(kegId)?.name.toString()
            canvas.drawText(
                name,
                nameX.toFloat(),
                nameY.toFloat(),
                paint
            )
            //mImageView.invalidate();
        }
    }


    private fun warnDisconnected(kegNumber: Int) {
        if (::kegSetDrawingData.isInitialized) {

            val kegX = kegSetDrawingData.kegs[kegNumber].x
            paint.color = Color.RED
            paint.setTextSize(textHeight)
            val path = Path()
            path.moveTo(kegX.toFloat(), textHeight)
            path.lineTo(kegX.toFloat(), kegSetDrawingData.kegHeight.toFloat())

            canvas.drawTextOnPath("Disconnected", path, 20F, -20F, paint)
        }
        //mImageView.invalidate();
    }

    fun getKegInPosition(position: Int): ConnectedKegScale? {

        val kegId = kegOrder.getKegIdInPosition(position)
        Log.i("getKegInPosition", "KegId $kegId")
        return kegScaleConnector.getKegScales(kegId)
    }
    fun spToPx(sp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            this.resources.displayMetrics
        ).toInt()
    }

    override fun onScanComplete(kegScales: HashMap<String, ConnectedKegScale>) {
        val pgsBar: ProgressBar = findViewById(R.id.spinner)
        pgsBar.visibility = View.INVISIBLE
        if (kegScales.size == 0) {
            Log.w("scancomplete", "No Keg Scales Detected")
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder
                .setMessage("No Keg Scales detected")
                .setTitle("Scan Failed")
                .setNeutralButton("Close") { dialog, which ->
                    Log.v("scan failed", "Warning dismissed")
                }

            val dialog: AlertDialog = builder.create()
            dialog.show()
            return
        }
        val preferences = getPreferences(Context.MODE_PRIVATE)
        kegOrder = KegOrder(preferences)
        Log.v("scancomplete", "Initial Keg Order: " + kegOrder.toString())
        val localKegList: MutableList<String> = emptyList<String>().toMutableList()
        for ((kegId, connectedKS) in kegScales) {
            if (kegId != "" && !kegOrder.contains(kegId)) {
                kegOrder.appendKeg(kegId)
            }
            localKegList.add(kegId)
        }

        Log.i("scancomplete", "Local Keg List: $localKegList")
        kegOrder.store()
        kegOrder.clearInactive(localKegList)
        initKegSetDrawingData(kegScales.size)
        Log.i("scancomplete", "scancallback" + kegOrder.toString())
    }

    override fun onKegRead(kegId: String, remaining: Int) {
        val fillPercentage = remaining.coerceIn(0,100)
        val kegNumber = kegOrder.indexOf(kegId)
        Log.i("KegFillDraw","KegNumber $kegNumber Filllevel $fillPercentage remaining $remaining")
        drawFillLevel(kegNumber, fillPercentage)
    }

    override fun onPour(kegId: String, poured: Int) {
        val kegNumber = kegOrder.indexOf(kegId)
        setLastPour(kegNumber, poured)
    }

    override fun onDisconnect(kegId: String) {
        val kegNumber = kegOrder.indexOf(kegId)
        warnDisconnected(kegNumber)
    }

    override fun onKegNameRead(kegId: String, name: String) {
        val kegNumber = kegOrder.indexOf(kegId)
        Log.v("Main", "Drawing Name $kegNumber")
        setName(kegNumber)
    }


    private fun checkAndRequestBluetoothPermissions() {
    // check required permissions - request those which have not already been granted
    val missingPermissionsToBeRequested = ArrayList<String>()
    if (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH
        ) != PackageManager.PERMISSION_GRANTED
    )
        missingPermissionsToBeRequested.add(Manifest.permission.BLUETOOTH)

    if (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_ADMIN
        ) != PackageManager.PERMISSION_GRANTED
    )
        missingPermissionsToBeRequested.add(Manifest.permission.BLUETOOTH_ADMIN)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // For Android 12 and above require both BLUETOOTH_CONNECT and BLUETOOTH_SCAN
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        )
            missingPermissionsToBeRequested.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        )
            missingPermissionsToBeRequested.add(Manifest.permission.BLUETOOTH_SCAN)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // FINE_LOCATION is needed for Android 10 and above
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        )
            missingPermissionsToBeRequested.add(Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        )
            missingPermissionsToBeRequested.add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    if (missingPermissionsToBeRequested.isNotEmpty()) {
        Log.i("Bluetooth", "Missing the following permissions: $missingPermissionsToBeRequested")
        ActivityCompat.requestPermissions(/* activity = */ this, /* permissions = */
            missingPermissionsToBeRequested.toTypedArray(),
            /* requestCode = */ 155
        )
    } else {
        Log.i("Bluetooth", "All required permissions GRANTED !")
    }
}

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i("PermissionsResult", "Callback $requestCode "+grantResults.joinToString(":"))
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            Log.i("Click", "click at x = " + event.getX() + " and y = " + event.getY())
            if (::kegSetDrawingData.isInitialized) {
                val kegNumber = this.kegSetDrawingData.coordinatesInKeg(
                    event.getX().toInt(),
                    event.getY().toInt()
                )
                Log.i("Click", "Keg " + kegNumber)
                if (kegNumber!! >=0) {
                    Log.i("Click", "Open Dialog")
                    val keg: ConnectedKegScale? = getKegInPosition(kegNumber)
                    val kegId = kegOrder.getKegIdInPosition(kegNumber)
                    if (keg != null) {
                        val dialog = ConfigureDialog.open(kegId, kegNumber, kegOrder, kegScaleConnector)
                        dialog.show(supportFragmentManager, ConfigureDialog.TAG)
                        dialog.setListener(this)

                    }
                }
            }
        }

        return super.onTouchEvent(event)
    }

    override fun onClose() {
        Log.i("Callback", "Closed")
        drawKegs()
    }
}


