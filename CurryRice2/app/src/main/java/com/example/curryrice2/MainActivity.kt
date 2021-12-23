package com.example.curryrice2

import android.Manifest.permission.RECORD_AUDIO
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import android.view.View
import android.widget.*

class MainActivity : AppCompatActivity() {
    private val dbName: String = "SampleDB"
    private val tableName: String = "SampleTable"
    private val dbVersion: Int = 1
    private var arrayListId: ArrayList<Int> = arrayListOf()
    private var arrayListName: ArrayList<String> = arrayListOf()
    private lateinit var editId: EditText
    private lateinit var editName: EditText
    private lateinit var buttonSelect: Button
    private lateinit var buttonInsert: Button
    // private lateinit var buttonUpdate: Button
    // private lateinit var buttonDelete: Button
    private lateinit var listView: ListView
    private lateinit var sampleDBAdapter: SampleDBAdapter

    private var stringArray: String = ""
    private var speechRecognizer : SpeechRecognizer? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        listView = findViewById(R.id.listView)

        // 音声認識部分
        val granted = ContextCompat.checkSelfPermission(this, RECORD_AUDIO)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(RECORD_AUDIO), PERMISSIONS_RECORD_AUDIO)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
        speechRecognizer?.setRecognitionListener(createRecognitionListenerStringStream { recognize_text_view.text = it })

        recognize_start_button.setOnClickListener {

            // ListView(データベース内容)を非表示
            listView.setVisibility(View.INVISIBLE)

            // TextViewを表示(復活)
            recognize_text_view.setVisibility(View.VISIBLE)

            speechRecognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
        }
        recognize_stop_button.setOnClickListener { speechRecognizer?.stopListening() }


        // Select実行部分
        buttonSelect = findViewById(R.id.buttonSelect)
        buttonSelect.setOnClickListener {
            // TextViewを非表示
            recognize_text_view.setVisibility(View.INVISIBLE)

            // ListView(データベース内容)を表示(復活)
            listView.setVisibility(View.VISIBLE)

            selectData()
            sampleDBAdapter.idList = arrayListId
            sampleDBAdapter.nameList = arrayListName
            sampleDBAdapter.notifyDataSetChanged()
        }

        // Insert実行部分
        buttonInsert = findViewById(R.id.buttonInsert)
        buttonInsert.setOnClickListener {

            if (stringArray != ""){
                insertData(stringArray)
            }

            // INSERT後は文字列をリセット
            stringArray = ""
        }

        sampleDBAdapter = SampleDBAdapter(this)

        listView.adapter = sampleDBAdapter
        listView.setOnItemClickListener { parent, view, position, id ->
            editId.setText(arrayListId.get(position), TextView.BufferType.NORMAL)
            editName.setText(arrayListName.get(position), TextView.BufferType.NORMAL)
        }
    }

    // SQLiteOpenHelperを継承したクラスを作成しデータベースを定義
    private class SampleDBHelper(context: Context, databaseName:String,
                                 factory: SQLiteDatabase.CursorFactory?, version: Int) :
        SQLiteOpenHelper(context, databaseName, factory, version) {

        // データベースが初期作成された時に発生するイベント
        // このサンプルでは以下のフィールドを持つSampleTableというテーブルを作成するCREATE TABLE文を実行
        override fun onCreate(database: SQLiteDatabase?) {
            database?.execSQL("create table if not exists SampleTable (id integer primary key autoincrement, name text)");
        }

        // SQLiteOpenHelperに渡されるバージョン番号が変わった時に発生するイベント
        // このサンプルではSampleTableに以下のフィールドを追加するALTER TABLE文を実行
        override fun onUpgrade(database: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
            if (oldVersion < newVersion) {
                database?.execSQL("alter table SampleTable add column deleteFlag integer default 0")
            }
        }
    }

    private fun insertData(name: String) {
            try {
            // 最初に作成したSQLLiteOpenHelperを継承したクラス
            val dbHelper = SampleDBHelper(applicationContext, dbName, null, dbVersion);

            // writableDatabaseメソッドを実行し、書き込み可能なSQLiteDatabaseを取得
            val database = dbHelper.writableDatabase

            // INSERTする値はContentValuesにセットしてinsertOrThrowメソッドに渡す
            val values = ContentValues()
            //values.put("id", id)       // 主キー
            values.put("name", name)   // 商品名

            database.insertOrThrow(tableName, null, values)
        }catch(exception: Exception) {
            Log.e("insertData", exception.toString())
        }
    }

    private fun selectData() {
        try {
            arrayListId.clear();
            arrayListName.clear();

            val dbHelper = SampleDBHelper(applicationContext, dbName, null, dbVersion)

            // readableDatabaseメソッドを実行し、読み取り専用のSQLiteDatabaseを取得
            val database = dbHelper.readableDatabase

            val sql = "select id, name from $tableName order by id"

            // 生のSQL文を実行するメソッド
            val cursor = database.rawQuery(sql, null) // 引数にSELECTを行うSQL文を設定

            // SQLの実行結果はCursorクラスで受け取る
            // moveToFirstメソッドで最初のレコードに移動
            // 最後のレコードに到達するまでmoveToNextメソッドを繰り返して全レコードの値を取得
            if (cursor.count > 0) {
                cursor.moveToFirst()
                while (!cursor.isAfterLast) {
                    arrayListId.add(cursor.getInt(0))
                    arrayListName.add(cursor.getString(1))
                    cursor.moveToNext()
                }
            }
        }catch(exception: Exception) {
            Log.e("selectData", exception.toString());
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
    }

    private fun createRecognitionListenerStringStream(onResult : (String)-> Unit) : RecognitionListener {
        return object : RecognitionListener {
            // The sound level in the audio stream has changed.
            override fun onRmsChanged(rmsdB: Float) {}

            // Called when the endpointer is ready for the user to start speaking.
            override fun onReadyForSpeech(params: Bundle) {
                //onResult("onReadyForSpeech")
                onResult("何か話してください")
            }

            // More sound has been received.
            override fun onBufferReceived(buffer: ByteArray) {
                onResult("onBufferReceived")
            }

            // Called when partial recognition results are available.
            override fun onPartialResults(partialResults: Bundle) {
                onResult("onPartialResults")
            }

            // Reserved for adding future events.
            override fun onEvent(eventType: Int, params: Bundle) {
                onResult("onEvent")
            }

            // The user has started to speak.
            override fun onBeginningOfSpeech() {
                // onResult("onBeginningOfSpeech")
                onResult("音声入力中")
            }

            // Called after the user stops speaking.
            override fun onEndOfSpeech() {
                // onResult("onEndOfSpeech")
                onResult("音声入力終了")
            }

            // A network or recognition error occurred.
            override fun onError(error: Int) {
                // onResult("onError")
                onResult("入力に失敗！")
            }

            // Called when recognition results are ready.
            override fun onResults(results: Bundle) {
                stringArray = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).toString();
                // onResult("onResults $stringArray")
                onResult("$stringArray")
            }
        }
    }

    companion object {
        private const val PERMISSIONS_RECORD_AUDIO = 1000
    }
}