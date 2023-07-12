package com.myapp.savewisely
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.integrity.internal.x
import com.google.android.play.integrity.internal.y
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import com.google.zxing.integration.android.IntentIntegrator
import org.apache.poi.hssf.usermodel.HeaderFooter.time
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


const val CAMERA_PERMISSION_REQUEST = 1001
var outputQrDate = 0.0
class QrcodeActivity : AppCompatActivity() {

    private lateinit var dbRef: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var invertedDate: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)

        val btn_qr_code_scanner: Button = findViewById(R.id.btn_qr_code_scanner)

        btn_qr_code_scanner.setOnClickListener {
            val scanner = IntentIntegrator(this)
            scanner.setOrientationLocked(true)
            scanner.setPrompt("Scan a barcode.")
            scanner.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
            scanner.setBarcodeImageEnabled(true)
            scanner.initiateScan()

        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    fun calcResult (x: String){
        val qrData = x.split("&")
        var qrdate = ""
        val a = qrData[0]
        for (i in 2..9)
            qrdate += a[i]

        val inputFormat = java.text.SimpleDateFormat("yyyyMMdd")
        //val outputFormat = java.text.SimpleDateFormat("dd/MM/yyyy")

        //var outputDateStr = outputFormat.parse(outputFormat.format(qrdate))

        qrdate = ""
        val b = qrData[1]
        for (i in 2..7)
            qrdate += b[i]
        outputQrDate = qrdate.toDouble() // Сумма
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val lblscan: TextView = findViewById(R.id.lbl_scan)
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK){
            val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
            if(result != null) {
                if(result.contents == null) {
                    Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
                } else {
                    //lblscan.text = outputDateStr.toString()
                    calcResult(result.contents.toString())
                    saveTransactionData(outputQrDate)
                }
            } else {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    private fun saveTransactionData (y: Double) {
        val title = "Скан чека"
        //val date = x.time
        val note = ""

        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        val currentDate = sdf.parse(sdf.format(System.currentTimeMillis()))
        val date = currentDate!!.time

        val type = 1
        val amount = y
        invertedDate = date * -1

        val listExpense = CategoryOptions.expenseCategory()
        val category = listExpense[10]

        val user = Firebase.auth.currentUser
        val uid = user?.uid
        if (uid != null) {
            dbRef = FirebaseDatabase.getInstance().getReference(uid)
        }
        auth = Firebase.auth

        val transactionID = dbRef.push().key!!
        val transaction =
            TransactionModel(transactionID, type, title, category, amount, date, note, invertedDate) //object of data class

        dbRef.child(transactionID).setValue(transaction)
            .addOnCompleteListener {
                Toast.makeText(this, "Data Inserted Successfully", Toast.LENGTH_LONG).show()
                finish()
            }.addOnFailureListener { err ->
                Toast.makeText(this, "Error ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }