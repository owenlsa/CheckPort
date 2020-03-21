package com.lsa.checkport;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {
    final public String SERVER_JSON = "config.json"; //保存的json配置文件名
    final public int TV_UPDATE_PERIOD = 500; //TextView自动刷新时间间隔

    private TextView textViewData1;
    private EditText etPort;
    private EditText etDomain;
    private RecyclerView recyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager layoutManager;

    private int totalPortAmount = 0;
    private int doneTimes = 0;
    private int okTimes = 0;
    private String failList = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //View里的模块定义
        textViewData1 = findViewById(R.id.textView1);
        etDomain = (EditText) findViewById(R.id.inDomain);
        etPort = (EditText) findViewById(R.id.inPort);

        recyclerView = (RecyclerView) findViewById(R.id.serverRecycler);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        recyclerView.setHasFixedSize(true);

        // use a linear layout manager
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);



        //声明定时刷新textView的Handler
        Handler mTimeHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == 0) {
                    textViewData1.setText("Scaning:  " + doneTimes + " / " + totalPortAmount
                            + "\nAmount of Open Ports: " + okTimes+ " / " + totalPortAmount
                            + "\n\nClose Ports:  " + failList);
                    sendEmptyMessageDelayed(0, TV_UPDATE_PERIOD);
                }
            }
        };
        //调用定时刷新Handler
        mTimeHandler.sendEmptyMessageDelayed(0,TV_UPDATE_PERIOD);

    }



    @SuppressLint("SetTextI18n")
    public void onScan(View view) {
        totalPortAmount = 0;
        doneTimes = 0;
        okTimes = 0;
        failList = "";
        final String strDomain = etDomain.getText().toString();
        final String strPort = etPort.getText().toString();
        if (strDomain.equals("") || strPort.equals("")) {
            Toast.makeText(this,"Please enter server and port.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                startScanner(strDomain, strPort);
            }
        }).start();
    }

    public void startScanner(String strDomain, String strPort) {
        String [] strPortArr = strPort.split(",");
        totalPortAmount = strPortArr.length;
        for (int i=0; i<totalPortAmount; i++) {
            try {
                int port = Integer.parseInt(strPortArr[i]);
                InetAddress inetAddress = InetAddress.getByName(strDomain);
                String ip = inetAddress.getHostAddress();

                int output = ScannerPortisAlive(ip, port);
                if (output == 1) {
                    okTimes = okTimes + 1;
                } else {
                    failList = failList + port + ",";
                }
                doneTimes = doneTimes + 1;

            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }


    }

    static public int ScannerPortisAlive(String ip, int port){
        int result=1; //1 for open, 0 for close
        try{
            Socket socket=new Socket();
            SocketAddress address=new InetSocketAddress(ip, port);
            socket.connect(address,2000);
            socket.close();
        } catch (IOException e) {
            result = 0; //set 0 for close
        }

        return result;
    }

    public void onAddJSON(View view) {
        final String addDomain = etDomain.getText().toString();
        final String addPort = etPort.getText().toString();
        if (addDomain.equals("") || addPort.equals("")) {
            Toast.makeText(this,"Please enter server and port.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                writeJson(addDomain, addPort,SERVER_JSON);
            }
        }).start();
    }


    public void writeJson(String getDomain, String getPort, String getFilename) {
        //json文件存放路径
        String sdPath = getExternalFilesDir(null).toString() + "/" + getFilename;
        System.out.println("filePath:" + sdPath);//查看实际路径
        try {
            // 开始读JSON数据
            System.out.println("开始读取JSON数据");
            FileInputStream fileInputStream = new FileInputStream(sdPath);
            //InputStreamReader 将字节输入流转换为字符流
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
            //包装字符流,将字符流放入缓存里
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            //StringBuilder和StringBuffer功能类似,存储字符串
            StringBuilder strBuilder = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                //append 被选元素的结尾(仍然在内部)插入指定内容,缓存的内容依次存放到builder中
                strBuilder.append(line);
            }
            bufferedReader.close();
            inputStreamReader.close();
            //builder.toString()返回表示此序列中数据的字符串
            JSONArray jsonArray = new JSONArray(strBuilder.toString());
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("domain",getDomain);
            jsonObject.put("port",getPort);
            jsonArray.put(jsonObject);
            String context = jsonArray.toString();
            WriteSDFile(context, getFilename);


        } catch (FileNotFoundException e) {//文件不存在，初始化
            try {
                JSONArray jsonArray = new JSONArray();
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("domain",getDomain);
                jsonObject.put("port",getPort);
                jsonArray.put(jsonObject);
                String context = jsonArray.toString();
                WriteSDFile(context, getFilename);
            } catch (Exception ee) {
                ee.printStackTrace();
            }


        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public void WriteSDFile(String context, String filename) {
        try {
            File sdPath =  getExternalFilesDir(null); //获取路径 /Android/data/包名/
            if (!sdPath.exists()) {
                return;
            }
            File newFile = new File(sdPath, filename);
            if (newFile.createNewFile()) {
            }
            FileOutputStream outStream = new FileOutputStream(newFile);
            outStream.write(context.getBytes());
            outStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onLoadJSON(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                loadJson(SERVER_JSON);
            }
        }).start();
    }

    public void loadJson(String getFilename) {
        //json文件存放路径
        String sdPath = getExternalFilesDir(null).toString() + "/" + getFilename;
        System.out.println("filePath:" + sdPath);//查看实际路径
        try {
            // 开始读JSON数据
            System.out.println("开始读取JSON数据");
            FileInputStream fileInputStream = new FileInputStream(sdPath);
            //InputStreamReader 将字节输入流转换为字符流
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
            //包装字符流,将字符流放入缓存里
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            //StringBuilder和StringBuffer功能类似,存储字符串
            StringBuilder strBuilder = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                //append 被选元素的结尾(仍然在内部)插入指定内容,缓存的内容依次存放到builder中
                strBuilder.append(line);
            }
            bufferedReader.close();
            inputStreamReader.close();
            //builder.toString()返回表示此序列中数据的字符串
            JSONArray jsonArray = new JSONArray(strBuilder.toString());
            JSONObject jsonObject = new JSONObject();
//            jsonObject.put("domain",getDomain);
//            jsonObject.put("port",getPort);
            jsonArray.put(jsonObject);
            String context = jsonArray.toString();
            WriteSDFile(context, getFilename);


        } catch (FileNotFoundException e) {//文件不存在，初始化
            try {
                JSONArray jsonArray = new JSONArray();
                JSONObject jsonObject = new JSONObject();
//                jsonObject.put("domain",getDomain);
//                jsonObject.put("port",getPort);
                jsonArray.put(jsonObject);
                String context = jsonArray.toString();
                WriteSDFile(context, getFilename);
            } catch (Exception ee) {
                ee.printStackTrace();
            }


        } catch (Exception e) {
            e.printStackTrace();
        }


    }

}
