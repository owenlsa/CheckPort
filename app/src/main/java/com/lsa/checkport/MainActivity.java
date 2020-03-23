package com.lsa.checkport;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class MainActivity extends AppCompatActivity {

    final protected String SERVER_JSON = "config.json"; //保存的json配置文件名
    final protected int TV_UPDATE_PERIOD = 300; //TextView自动刷新时间间隔

    protected JSONArray jsonArrayCurrent;
    private TextView textViewData1;
    private EditText etPort;
    private EditText etDomain;
    private ListView listView;
    private String[] myDataset;
    private int UPDATE_LIST = 0;
    private int SCANNING = 0;

    private int totalPortAmount = 0;
    private int doneTimes = 0;
    private int okTimes = 0;
    private String failList = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //View里的模块定义
        textViewData1 = findViewById(R.id.textViewResult);
        etDomain = findViewById(R.id.inDomain);
        etPort = findViewById(R.id.inPort);
        listView= findViewById(R.id.serverList);

        loadJson(SERVER_JSON); //获取json，初始化jsonArrayCurrent

        if (jsonArrayCurrent != null) {
            myDataset = new String[jsonArrayCurrent.length()];
            for (int i = 0; i < jsonArrayCurrent.length(); i++) {
                try {
                    //提取jsonArrayCurrent中的内容到myDataset以供listView显示
                    JSONObject jsonObject = jsonArrayCurrent.getJSONObject(i);
                    myDataset[i] = jsonObject.getString("domain") + "\n"
                    + jsonObject.getString("port");
                } catch (Exception e) {
                    System.out.println("Error 101");
                    e.printStackTrace();
                }
            }
            updateListView(); //刷新listView
        }

        //声明定时刷新textView的Handler
        Handler mTimeHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == 0) {
                    textViewData1.setText("Scanning Ports:  " + doneTimes + " / " + totalPortAmount
                            + "\nAmount of Open Ports: " + okTimes+ " / " + totalPortAmount
                            + "\nClose Ports:  " + failList); //刷新result
                    onUpdateListView(); //刷新listView
                    sendEmptyMessageDelayed(0, TV_UPDATE_PERIOD);
                }
            }
        };
        //调用定时刷新Handler
        mTimeHandler.sendEmptyMessageDelayed(0,TV_UPDATE_PERIOD);
    }


    public void onUpdateListView() {
        if (UPDATE_LIST == 1){ //仅在jsonArrayCurrent有修改时进行listView的刷新
            updateListView();
            UPDATE_LIST = 0;
        }

    }


    public void updateListView() {
        //定义ArrayAdapter
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                R.layout.list_item, R.id.textViewList, myDataset);
        listView.setAdapter(adapter); //给listView设置ArrayAdapter
        //监听点击
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                try {
                    //提取jsonArrayCurrent中对应的domain和port，然后set给输入文本框
                    JSONObject jsonObject = jsonArrayCurrent.getJSONObject(position);
                    etDomain.setText(jsonObject.getString("domain"));
                    etPort.setText(jsonObject.getString("port"));
                } catch (Exception e) {
                    System.out.println("Error 401");
                    e.printStackTrace();
                }
            }
        });
        //监听长按
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l) {
                //删除json内条目并toast提示
                //String value=adapter.getItem(position);
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                onDelJSON(position);//删除json内条目
                Toast.makeText(getApplicationContext(),"Deleted",Toast.LENGTH_SHORT).show();
                return false;
            }
        });
    }


    @SuppressLint("SetTextI18n")
    public void onScan(View view) {
        if (SCANNING == 0) {
            totalPortAmount = 0;
            doneTimes = 0;
            okTimes = 0;
            failList = "";
            SCANNING = 1;
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
        } else {
            Toast.makeText(this,"Already scanning, please wait.",
                    Toast.LENGTH_SHORT).show();
        }

    }


    public void startScanner(String strDomain, String strPort) {
        String [] strPortArr = strPort.split(","); //以逗号分隔端口
        totalPortAmount = strPortArr.length; //保存总端口个数
        for (int i=0; i<totalPortAmount; i++) {
            try {
                int port = Integer.parseInt(strPortArr[i]);
                InetAddress inetAddress = InetAddress.getByName(strDomain);
                String ip = inetAddress.getHostAddress();

                int output = ScannerPortisAlive(ip, port);
                if (output == 1) {
                    okTimes = okTimes + 1; //成功连接端口计数
                } else {
                    failList = failList + port + ","; //失败端口统计
                }
                doneTimes = doneTimes + 1; //已完成端口计数

            } catch (Exception e) {
                SCANNING = 0;
                System.out.println("Error 102");
                e.printStackTrace();
            }
        }
        SCANNING = 0;
    }


    //返回端口是否链接成功，1成功，0失败
    public int ScannerPortisAlive(String ip, int port){
        int result=1; //1 for open, 0 for close
        try{
            Socket socket=new Socket(); //新建socket
            SocketAddress address=new InetSocketAddress(ip, port); //设置连接的ip和端口
            socket.connect(address,2000); //尝试连接，超时2000ms
            socket.close();
        } catch (Exception e) {
            System.out.println("Error 103");
            result = 0; //set 0 for close
        }
        return result;
    }


    public void onAddJSON(View view) {
        final String addDomain = etDomain.getText().toString(); //拿editText中的domian和port
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
            //这里如果strBuilder是空，会JSONException
            JSONArray jsonArray = new JSONArray(strBuilder.toString());

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("domain",getDomain);
            jsonObject.put("port",getPort);
            jsonArray.put(jsonObject);
            jsonArrayCurrent = jsonArray; //更新jsonArrayCurrent用来更新列表
            jsonArrayExplain(); //更新myDataset
            String context = jsonArray.toString();
            WriteSDFile(context, getFilename); //重新输出到json


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
                System.out.println("Error 201");
                ee.printStackTrace();
            }



        } catch (JSONException e) { //这里strBuilder是空
            try {
                JSONArray jsonArray = new JSONArray();

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("domain",getDomain);
                jsonObject.put("port",getPort);
                jsonArray.put(jsonObject);
                jsonArrayCurrent = jsonArray; //更新jsonArrayCurrent用来更新列表
                jsonArrayExplain(); //更新myDataset
                String context = jsonArray.toString();
                WriteSDFile(context, getFilename); //重新输出到json
            } catch (Exception ee) {
                System.out.println("Error 202");
                ee.printStackTrace();
            }

        }
        catch (Exception e) {
            System.out.println("Error 104");
            e.printStackTrace();
        }


    }

    public void WriteSDFile(String context, String filename) {
        try {
            File sdPath =  getExternalFilesDir(null); //获取路径 /Android/data/包名/
            if (!sdPath.exists()) { //如果路径不存在，返回
                System.out.println("Error 105");
                return;
            }
            File newFile = new File(sdPath, filename);
            if (newFile.createNewFile()) {
            }
            FileOutputStream outStream = new FileOutputStream(newFile);
            outStream.write(context.getBytes());
            outStream.close();
        } catch (Exception e) {
            System.out.println("Error 106");
            e.printStackTrace();
        }
    }



    public void loadJson(String getFilename) {
        //json文件存放路径
        String sdPath = getExternalFilesDir(null).toString() + "/" + getFilename;
        try {
            // 开始读JSON数据
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
            if (strBuilder.length() != 0) {
                jsonArrayCurrent = new JSONArray(strBuilder.toString());
            }

        } catch (FileNotFoundException e) {//文件不存在，初始化
                    WriteSDFile(null, getFilename);
        }  catch (Exception e) {
            System.out.println("Error 301");
            e.printStackTrace();
        }

        if (jsonArrayCurrent != null) {
            String[] myDataset = new String[jsonArrayCurrent.length()];
            for (int i = 0; i < jsonArrayCurrent.length(); i++) {
                try {
                    JSONObject jsonObject = jsonArrayCurrent.getJSONObject(i);
                    myDataset[i] = jsonObject.getString("domain") + "\n"
                            + jsonObject.getString("port");
                } catch (Exception e) {
                    System.out.println("Error 302");
                    e.printStackTrace();
                }
            }
        }
    }


    public void jsonArrayExplain() { //jsonArrayCurrent解析到myDataset
        if (jsonArrayCurrent != null) {
            myDataset = new String[jsonArrayCurrent.length()];
            for (int i = 0; i < jsonArrayCurrent.length(); i++) {
                try {
                    JSONObject jsonObject = jsonArrayCurrent.getJSONObject(i);
                    myDataset[i] = jsonObject.getString("domain") + "\n"
                            + jsonObject.getString("port");
                } catch (Exception e) {
                    System.out.println("Error 303");
                    e.printStackTrace();
                }
            }
            UPDATE_LIST = 1; //jsonArrayCurrent有修改的标志
        }
    }


    public void onDelJSON(final int position) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                delJson(position,SERVER_JSON);
            }
        }).start();
    }


    public void delJson(int position, String getFilename) {
        //json文件存放路径
        String sdPath = getExternalFilesDir(null).toString() + "/" + getFilename;
        try {
            // 开始读JSON数据
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
            jsonArray.remove(position);
            jsonArrayCurrent = jsonArray; //更新jsonArrayCurrent用来更新列表
            jsonArrayExplain(); //更新myDataset
            String context = jsonArray.toString();
            WriteSDFile(context, getFilename); //重新输出到json

        }
        catch (Exception e) {
            System.out.println("Error 304");
            e.printStackTrace();
        }
    }
}
