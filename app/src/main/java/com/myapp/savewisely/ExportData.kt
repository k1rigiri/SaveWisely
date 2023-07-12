package com.myapp.savewisely

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.style.DynamicDrawableSpan.ALIGN_CENTER
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.util.Pair
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import org.apache.poi.hssf.usermodel.HSSFCellStyle
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hssf.util.HSSFColor
import org.apache.poi.ss.usermodel.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
class ExportData : AppCompatActivity() {

    private var dateStart: Long = 0
    private var dateEnd: Long = 0

    private var TAG: String = "ExcelUtil"
    private lateinit var cell: Cell
    private lateinit var workbook: Workbook
    private lateinit var sheet: Sheet
    private lateinit var headerCellStyle: CellStyle

    private var user = Firebase.auth.currentUser
    private val uid = user?.uid //Получение id пользователя из БД
    private var dbRef: DatabaseReference = FirebaseDatabase.getInstance().getReference(uid!!)

    private val tagPermission: String? = ExportData::class.java.simpleName
    private val PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private lateinit var fileName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export_data)

        backButtonClicked()

        setInitDateRange()

        dateRangePicker()

        //Экспорт в эксель файл
        val exportButton: Button = findViewById(R.id.exportBtn)
        exportButton.setOnClickListener {
            fileName = "Save_Wisely_" + convertDateFileName(dateStart, dateEnd) + ".xls"
            val isPermissionGranted = checkPermissionsAtRuntime()
            if (isPermissionGranted){
                exportDataIntoWorkbook()
            }else{
                inflateAlertDialog()
                Snackbar.make(it, "Разрешение не выдано", Snackbar.LENGTH_LONG).show()
            }

        }
    }

    private fun exportDataIntoWorkbook() {

        if (!isExternalStorageAvailable() || isExternalStorageReadOnly()) {
            Log.e(TAG, "Storage not available or read only")

        }

        workbook = HSSFWorkbook()

        setHeaderCellStyle()
        sheet = workbook.createSheet("Transactions")
        sheet.setColumnWidth(0, (15 * 230))
        sheet.setColumnWidth(1, (15 * 230))
        sheet.setColumnWidth(2, (15 * 400))
        sheet.setColumnWidth(3, (15 * 400))
        sheet.setColumnWidth(4, (15 * 400))
        sheet.setColumnWidth(5, (15 * 500))

        setHeaderRow()
        fillDataIntoExcel()
    }

    private fun storeExcelInStorage() {
        var isExcelGenerated: Boolean
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        var fileOutputStream: FileOutputStream? = null

        try {
            fileOutputStream = FileOutputStream(file)
            workbook.write(fileOutputStream)
            Log.e(TAG, "Writing file$file")
            isExcelGenerated = true
        } catch (e: IOException) {
            Log.e(TAG, "Error writing Exception: ", e)
            isExcelGenerated = false
        } catch (e: Exception) {
            Log.e(TAG,"Failed to save file due to Exception: ", e)
            isExcelGenerated = false
        } finally {
            try {
                fileOutputStream?.close()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        if (isExcelGenerated){
            Snackbar.make(findViewById(android.R.id.content), "Эксель файл успешно был добавлен в загрузочную папку", Snackbar.LENGTH_LONG).show()
        }else{
            Toast.makeText(this, "Ошибка экспорта!", Toast.LENGTH_LONG).show()
        }
    }

    private fun fillDataIntoExcel() {
        val transactionList: ArrayList<TransactionModel> = arrayListOf()

        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                transactionList.clear()
                if (snapshot.exists()) {
                    for (transactionSnap in snapshot.children) {
                        val transactionData = transactionSnap.getValue(TransactionModel::class.java) //reference data class
                        if (transactionData!!.date!! > dateStart-86400000 &&
                            transactionData.date!!<= dateEnd){
                            transactionList.add(transactionData)
                        }
                    }

                    if (transactionList.isEmpty()){
                        Snackbar.make(findViewById(android.R.id.content), "В диапазоне " + convertDate(dateStart, dateEnd) + " нет данных.", Snackbar.LENGTH_LONG).show()
                    }else{
                        for ((i) in transactionList.withIndex()){
                            val rowData: Row = sheet.createRow(i+1)

                            cell = rowData.createCell(0)
                            val simpleDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
                            val result = Date(transactionList[i].date!!)
                            cell.setCellValue(simpleDateFormat.format(result))

                            cell = rowData.createCell(1)
                            if (transactionList[i].type == 1){
                                cell.setCellValue("Expense")
                            }else{
                                cell.setCellValue("Income")
                            }

                            cell = rowData.createCell(2)
                            cell.setCellValue(transactionList[i].amount.toString())

                            cell = rowData.createCell(3)
                            cell.setCellValue(transactionList[i].title)

                            cell = rowData.createCell(4)
                            cell.setCellValue(transactionList[i].category)

                            cell = rowData.createCell(5)
                            cell.setCellValue(transactionList[i].note)
                        }
                        storeExcelInStorage()
                    }
                }else{
                    Snackbar.make(findViewById(android.R.id.content), "Нет данных транзакций", Snackbar.LENGTH_LONG).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
    }

    private fun isExternalStorageAvailable(): Boolean {
        val externalStorageState: String = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == externalStorageState
    }

    private fun isExternalStorageReadOnly(): Boolean {
        val externalStorageState = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED_READ_ONLY == externalStorageState
    }

    private fun setHeaderCellStyle() {
        headerCellStyle = workbook.createCellStyle()
        headerCellStyle.fillForegroundColor = HSSFColor.BLUE.index
        headerCellStyle.fillPattern = HSSFCellStyle.SOLID_FOREGROUND
        headerCellStyle.alignment = CellStyle.ALIGN_CENTER
    }

    private fun setHeaderRow() {
        val headerRow: Row = sheet.createRow(0)

        cell = headerRow.createCell(0)
        cell.setCellValue("Date")
        cell.cellStyle = headerCellStyle

        cell = headerRow.createCell(1)
        cell.setCellValue("Type")
        cell.cellStyle = headerCellStyle

        cell = headerRow.createCell(2)
        cell.setCellValue("Amount")
        cell.cellStyle = headerCellStyle

        cell = headerRow.createCell(3)
        cell.setCellValue("Title")
        cell.cellStyle = headerCellStyle

        cell = headerRow.createCell(4)
        cell.setCellValue("Category")
        cell.cellStyle = headerCellStyle

        cell = headerRow.createCell(5)
        cell.setCellValue("Note")
        cell.cellStyle = headerCellStyle
    }

    private fun setInitDateRange() {
        val dateRangeEt: EditText = findViewById(R.id.dateRangeEt)

        val currentDate = Date()
        val cal: Calendar = Calendar.getInstance(TimeZone.getDefault())
        cal.time = currentDate

        val startDay = cal.getActualMinimum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, startDay)
        val startDate = cal.time
        dateStart= startDate.time

        val endDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, endDay)
        val endDate = cal.time
        dateEnd= endDate.time

        dateRangeEt.hint = "Этот месяц"
    }

    private fun dateRangePicker() {
        val dateRangeEt: EditText = findViewById(R.id.dateRangeEt)

        dateRangeEt.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Выберите дату")
                .setSelection(
                    Pair(
                        dateStart,
                        dateEnd
                    )
                ).build()
            datePicker.show(supportFragmentManager, "DatePicker")
            datePicker.addOnPositiveButtonClickListener {
                val dateString = datePicker.selection.toString()
                val date: String = dateString.filter { it.isDigit() }
                dateStart = date.substring(0,13).toLong()
                dateEnd  = date.substring(13).toLong()
                dateRangeEt.hint = convertDate(dateStart, dateEnd)
            }
        }
    }

    private fun convertDate(dateStart: Long, dateEnd: Long): String {
        val simpleDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        val date1 = Date(dateStart)
        val date2 = Date(dateEnd)
        val result1 = simpleDateFormat.format(date1)
        val result2 = simpleDateFormat.format(date2)
        return "$result1 - $result2"
    }

    private fun convertDateFileName(dateStart: Long, dateEnd: Long): String {
        val simpleDateFormat = SimpleDateFormat("ddMMyyyy", Locale.ENGLISH)
        val date1 = Date(dateStart)
        val date2 = Date(dateEnd)
        val result1 = simpleDateFormat.format(date1)
        val result2 = simpleDateFormat.format(date2)
        return "${result1}_$result2"
    }

    private fun backButtonClicked() {
        val backButton: ImageButton = findViewById(R.id.backBtn)
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun requestPermissions() {
        Log.e(tagPermission, "requestPermissions: ")
        ActivityCompat.requestPermissions(this, PERMISSIONS, 25)
    }

    private fun checkPermissionsAtRuntime(): Boolean {
        Log.e(tagPermission, "checkPermissionsAtRuntime: ")
        for (permission in PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    private fun inflateAlertDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Mandatory")
            .setMessage("Kindly enable all permissions through Settings")
            .setPositiveButton("OKAY") { dialogInterface: DialogInterface, _: Int ->
                launchAppSettings()
                dialogInterface.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun launchAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivityForResult(intent, 20)
    }

    override fun onStart() {
        super.onStart()

        requestPermissions()
    }
}
