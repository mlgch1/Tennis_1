
package com.scorer.tennis;

// From pre WiFi version 15/08/2016

// 8 Inch Branch - 01/04/2018

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Process;
import android.support.v4.content.ContextCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.scorer.tennis.R.id;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.List;

import static android.os.SystemClock.sleep;
import static com.scorer.tennis.Config.context;
import static com.scorer.tennis.GlobalClass.getClub;
import static com.scorer.tennis.GlobalClass.getInc;
import static com.scorer.tennis.GlobalClass.getSSID;
import static com.scorer.tennis.GlobalClass.getStartTesting;
import static com.scorer.tennis.GlobalClass.getTest;
import static com.scorer.tennis.GlobalClass.getThresh;
import static com.scorer.tennis.GlobalClass.setChannel;
import static com.scorer.tennis.GlobalClass.setClub;
import static com.scorer.tennis.GlobalClass.setInc;
import static com.scorer.tennis.GlobalClass.setSSID;
import static com.scorer.tennis.GlobalClass.setTest;
import static com.scorer.tennis.GlobalClass.setThresh;

// ******************************************************************************

public class MainActivity extends Activity {

    private AlertDialog ad = null;
    private DBAdapter myDb;
    private TextView t;

    private int c_points_h;         // current counters
    private int c_points_v;
    private int c_games_h;
    private int c_games_v;
    private int c_sets_h;
    private int c_sets_v;

    private int h_points_h;       // historic set of counters
    private int h_points_v;
    private int h_games_h;
    private int h_games_v;
    private int h_sets_h;
    private int h_sets_v;

    private String server = "Z";
    private String h_server;
    private String strPlayerButton = "Z";

    private boolean matchComplete = false;

    // Timers
    private resumeTimer res_counter;
    private boolean res_timerActive = false;

    private flashTimer flash_counter;

    private illegal_flashTimer illegal_flash_counter;

    private server_flashTimer server_flash_counter;

    private wifiTimer wifi_counter;
    private String WifiSSID;
    private boolean connect_in_progress = false;

    public boolean wifiConnected = false;
    public boolean wifiEnabled = false;
    private Socket socket;

    private SendThread sendThread;
    private boolean stopSendThread = false;

    private ReceiveThread receiveThread;
    private boolean stopRecvThread = false;

    private boolean stopRecv = false;

    private DataOutputStream dataOut;
    private BufferedReader dataIn;

    private buttonTimer button_counter;

    private batteryTimer battery_counter;
    private int batteryTestLevel;
    private int batteryMessageNo = 5;

    private ImageView i;

    private boolean flipFlag = false;
    private int flipCntr = 2;



    private int no_Sets = 0;
    private int set_Type = 0;           // 0 - Advantage Sets       1 - Tie Break Sets
    private int last_Set_Type = 0;
    private int game_Type = 0;          // current game
    private boolean last_Set = false;




//    private boolean advSet = true;



    private int minGamesToWinSet = 0;
    private int nextToLastSet = 0;
    private int setsToWinPerPlayer = 0;


//    private int mtb_points;

    private boolean advLastSet = true;
    private boolean noAdv = false;
    //    private boolean matchTb;
    private boolean ShortSets = false;

    private String c_audio;
    private String c_audio_temp;

/*
    G - Game
    S - Set
    M - Match
    D - Deuce
    A - Advantage Server
    a - Advantage Receiver
    C - Change Ends
    X - Nothing
*/

//    private int set_type;
//    private int last_Set_Type;
    private int fast4;
    private int no_adv;
    private int short_sets;
    private int match_tb_game;
    private int mtb_7;
    private int mtb_10;

// ******************************************************************************

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(MainActivity.this, SplashActivity.class);
        startActivity(intent);
        setContentView(R.layout.activity_main);

        context = this;
        myDb = new DBAdapter(this);
        myDb.open();

        setGlobals();

        if (getClub().contains("ILLEGAL")) {
            start_illegal_flashTimer();
        }

        myDb.K_Log("Start App");

        start_ResumeTimer();    // Delay onResume() for things to settle

        batteryTestLevel = getThresh();
        start_batteryTimer();

        WifiSSID = "scorer_" + getSSID();

        start_wifiTimer();

//        GameNotice(0);





        // Data Base Viewer - To be deleted  ********************************************************

        TextView tv = (TextView) findViewById(R.id.id_wifi_message);

        tv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                Intent dbmanager = new Intent(MainActivity.this, AndroidDatabaseManager.class);
                startActivity(dbmanager);
            }
        });


/*
 To be deleted  ***********************************************************************************
        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }
*/


    }

// ******************************************************************************

    @Override
    public void onBackPressed() {
    }

// ******************************************************************************

    @Override
    protected void onDestroy() {

        myDb.K_Log("Close App");
        myDb.close();

        stop_batteryTimer();
        stop_wifiTimer();

        stopSendThread = true;
        stopRecvThread = true;

        super.onDestroy();
    }

// ******************************************************************************

    private void setGlobals() {

        setThresh(myDb.readSystem(DBAdapter.KEY_SYSTEM_BATT_THRESH));
        setInc(myDb.readSystem(DBAdapter.KEY_SYSTEM_BATT_INC));

        setSSID(myDb.readSystem(DBAdapter.KEY_SYSTEM_SSID));
        setChannel(myDb.readSystem(DBAdapter.KEY_SYSTEM_CHANNEL));

        setClub(myDb.readSystemStr(DBAdapter.KEY_SYSTEM_CLUB));

    }

// ******************************************************************************

    @Override
    protected void onResume() {
        super.onResume();

        if (res_timerActive) return;

        myDb.close();
        myDb.open();

        myDb.K_Log("Resume App");

        setupGameVariables();

        // *********** Club

        t = (TextView) findViewById(id.id_club);
        t.setText(getClub());

        if (getClub().contains("ILLEGAL")) {
            start_illegal_flashTimer();
        } else {
            stop_illegal_flashTimer();
        }

        // *********** Player Names

        String s = myDb.readSystemStr(DBAdapter.KEY_SYSTEM_NAME_A);
        t = (TextView) findViewById(id.id_name_a);
        t.setText(s);

        t = (TextView) findViewById(id.id_A_Team);
        t.setText(s);

        s = myDb.readSystemStr(DBAdapter.KEY_SYSTEM_NAME_B);
        t = (TextView) findViewById(id.id_name_b);
        t.setText(s);

        t = (TextView) findViewById(id.id_B_Team);
        t.setText(s);

        // *********** Scores

        s = myDb.readSystemStr(DBAdapter.KEY_SYSTEM_POINTS_A);
        t = (TextView) findViewById(id.id_points_a);
        if (game_Type == 0) {
            c_points_h = Integer.parseInt(s);
            s = convert_Points(c_points_h);
        }
        t.setText(s);

        s = myDb.readSystemStr(DBAdapter.KEY_SYSTEM_POINTS_B);
        t = (TextView) findViewById(id.id_points_b);
        if (game_Type == 0) {
            c_points_v = Integer.parseInt(s);
            s = convert_Points(c_points_v);
        }
        t.setText(s);

        s = myDb.readSystemStr(DBAdapter.KEY_SYSTEM_GAMES_A);
        t = (TextView) findViewById(id.id_games_a);
        t.setText(s);
        c_games_h = Integer.parseInt(s);

        s = myDb.readSystemStr(DBAdapter.KEY_SYSTEM_GAMES_B);
        t = (TextView) findViewById(id.id_games_b);
        t.setText(s);
        c_games_v = Integer.parseInt(s);

        s = myDb.readSystemStr(DBAdapter.KEY_SYSTEM_SETS_A);
        t = (TextView) findViewById(id.id_sets_a);
        t.setText(s);
        c_sets_h = Integer.parseInt(s);

        s = myDb.readSystemStr(DBAdapter.KEY_SYSTEM_SETS_B);
        t = (TextView) findViewById(id.id_sets_b);
        t.setText(s);
        c_sets_v = Integer.parseInt(s);

        // Server

        server = myDb.readSystemStr(DBAdapter.KEY_SYSTEM_SERVER);

        if (server == null) server = "Z";

        if (server.equals("Z")) {
            start_server_flashTimer();
        }

        switch (server) {
            case "H":
                t = (TextView) findViewById(id.id_server_mark_a);
                t.setVisibility(View.VISIBLE);

                t = (TextView) findViewById(id.id_server_mark_b);
                t.setVisibility(View.INVISIBLE);

                break;

            case "V":
                t = (TextView) findViewById(id.id_server_mark_a);
                t.setVisibility(View.INVISIBLE);

                t = (TextView) findViewById(id.id_server_mark_b);
                t.setVisibility(View.VISIBLE);

                break;

            default:
                t = (TextView) findViewById(id.id_server_mark_a);
                t.setVisibility(View.INVISIBLE);

                t = (TextView) findViewById(id.id_server_mark_b);
                t.setVisibility(View.INVISIBLE);
        }

        // Rules

        // No of Sets

        t = (TextView) findViewById(id.id_no_sets);

        String setNo = "";

        if (no_Sets == 1) {
            setNo = getString(R.string.set1);
        }

        if (no_Sets == 3) {
            setNo = getString(R.string.set3);
        }

        if (no_Sets == 5) {
            setNo = getString(R.string.set5);
        }

        t.setText(setNo);

        // Type of Set

        t = (TextView) findViewById(id.id_no_adv);
        TextView tt = (TextView) findViewById(id.id_set_type);
        TextView ttt = (TextView) findViewById(id.id_last_set_type);

        tt.setVisibility(View.VISIBLE);

        if (set_Type == 0) {
            tt.setText(R.string.advsets);
        } else {
            tt.setText(R.string.tbsets);
        }

        // No Advantage

        if (no_adv == 1) {
            t.setVisibility(View.VISIBLE);
            tt.setVisibility(View.INVISIBLE);
            ttt.setVisibility(View.INVISIBLE);
        } else {
            t.setVisibility(View.INVISIBLE);
            tt.setVisibility(View.VISIBLE);
            ttt.setVisibility(View.VISIBLE);
        }

        // Last Set

        ttt.setVisibility(View.VISIBLE);

        if (set_Type == 1) {

            if (last_Set_Type == 0) {
                ttt.setText(R.string.lastsetadv);
            } else {
                ttt.setText(R.string.lastsettb);
            }
        } else {
            ttt.setVisibility(View.INVISIBLE);
        }

        // Match Tie Break

        t = (TextView) findViewById(id.id_mtb);

        if (match_tb_game == 1) {

            if (mtb_7 == 1) {
                t.setText(R.string.tb7);
            }

            if (mtb_10 == 1) {
                t.setText(R.string.tb10);
            }
        } else {
            match_tb_game = 0;

            t.setText("");
        }

        // Short Sets

        t = (TextView) findViewById(id.id_short_sets);

        if (short_sets == 1) {
            t.setVisibility(View.VISIBLE);
//            GameNotice(1);
        } else {
            t.setVisibility(View.INVISIBLE);
        }

        // FAST4

        t = (TextView) findViewById(id.id_fast4);

        if (fast4 == 1) {
            t.setVisibility(View.VISIBLE);
            rules_invisible();
        } else {
            t.setVisibility(View.INVISIBLE);
        }



        myDb.K_Log("no_Sets   " + no_Sets);
        myDb.K_Log("last_Set   " + last_Set);
        myDb.K_Log("game_Type   " + game_Type);
        myDb.K_Log("last_Set_Type   " + last_Set_Type);
        myDb.K_Log("minGamesToWinSet   " + minGamesToWinSet);
        myDb.K_Log("nextToLastSet   " + nextToLastSet);
        myDb.K_Log("setsToWinPerPlayer   " + setsToWinPerPlayer);
        myDb.K_Log("set_Type   " + set_Type);
        myDb.K_Log("advLastSet   " + advLastSet);
        myDb.K_Log("setsToWinPerPlayer   " + setsToWinPerPlayer);










    }

// ******************************************************************************

    private void rules_invisible() {
        t = (TextView) findViewById(id.id_set_type);
        t.setVisibility(View.INVISIBLE);

        t = (TextView) findViewById(id.id_last_set_type);
        t.setVisibility(View.INVISIBLE);

        t = (TextView) findViewById(id.id_no_adv);
        t.setVisibility(View.INVISIBLE);

        t = (TextView) findViewById(id.id_short_sets);
        t.setVisibility(View.INVISIBLE);

        t = (TextView) findViewById(id.id_mtb);
        t.setVisibility(View.INVISIBLE);

        t = (TextView) findViewById(id.id_notice);
        t.setVisibility(View.INVISIBLE);

        t = (TextView) findViewById(id.id_set_server);
        t.setVisibility(View.INVISIBLE);
    }

// ******************************************************************************

    public String convert_Points(int points) {
        String ss;

        if (game_Type == 0) {
            switch (points) {
                case 0:
                    ss = "00";
                    break;
                case 1:
                    ss = "15";
                    break;
                case 2:
                    ss = "30";
                    break;
                case 3:
                    ss = "40";
                    break;
                default:
                    ss = "AD";
            }
        } else {
            ss = Integer.toString(points);
        }

        return ss;
    }

// ******************************************************************************

    public String convert_Points_Transmit(int points) {
        String ss;

        if (game_Type == 0) {
            switch (points) {
                case 0:
                    ss = "00";
                    break;
                case 1:
                    ss = "15";
                    break;
                case 2:
                    ss = "30";
                    break;
                case 3:
                    ss = "40";
                    break;
                default:
                    ss = "AD";
            }
        } else {
            ss = points + "B";
            if (ss.length() > 2) {
                ss = ss.substring(0, 2);

                ss = ss.substring(1, 2) + ss.substring(0, 1);
            }
        }
        return ss;
    }

// ******************************************************************************
// Buttons in Action Bar
// ******************************************************************************

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    // ******************************************************************************

    public void onClick_Reset(View view) {

        myDb.K_Log("Reset");
        resetAlertDialog();

        onPause();
    }

// ******************************************************************************

    public void onClick_Rules(View view) {

        Intent intent = new Intent(MainActivity.this, PDFActivity.class);
        startActivity(intent);

        myDb.K_Log("Open Rules");
    }

// ******************************************************************************

    public void onClick_Other(View view) {

        Intent intent = new Intent(MainActivity.this, OtherActivity.class);
        startActivity(intent);

        onPause();

        myDb.K_Log("Open Other Menu");
    }

// ******************************************************************************

    public void onClick_Results(View view) {

        Intent intent = new Intent(MainActivity.this, ResultsActivity.class);
        startActivity(intent);

        myDb.K_Log("Open Results");
    }

// ******************************************************************************

    public void onClick_Exit(View view) {

        myDb.K_Log("Quit");
        quitAlertDialog();
    }

// ******************************************************************************

    public void onClick_name_a(View view) {

        Intent intent = new Intent(MainActivity.this, Name_A_Activity.class);
        startActivity(intent);

        myDb.K_Log("Open Name A Window");
    }

// ******************************************************************************

    public void onClick_name_b(View view) {

        Intent intent = new Intent(this, Name_B_Activity.class);
        startActivity(intent);
        myDb.K_Log("Open Name B Window");
    }

// ******************************************************************************

    public void onClick_server_a(View view) {

        server = "H";
        myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_SERVER, server);

        t = (TextView) findViewById(id.id_server_mark_a);
        t.setVisibility(View.VISIBLE);
        t = (TextView) findViewById(id.id_server_mark_b);
        t.setVisibility(View.INVISIBLE);

        stop_server_flashTimer();
    }

// ******************************************************************************

    public void onClick_server_b(View view) {

        server = "V";
        myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_SERVER, server);

        t = (TextView) findViewById(id.id_server_mark_a);
        t.setVisibility(View.INVISIBLE);
        t = (TextView) findViewById(id.id_server_mark_b);
        t.setVisibility(View.VISIBLE);

        stop_server_flashTimer();
    }

// ******************************************************************************

    public void flip_server() {

        switch (server) {
            case "H":
                server = "V";

                myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_SERVER, server);

                t = (TextView) findViewById(id.id_server_mark_a);
                t.setVisibility(View.INVISIBLE);

                t = (TextView) findViewById(id.id_server_mark_b);
                t.setVisibility(View.VISIBLE);
                break;

            case "V":

                server = "H";

                myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_SERVER, server);

                t = (TextView) findViewById(id.id_server_mark_a);
                t.setVisibility(View.VISIBLE);

                t = (TextView) findViewById(id.id_server_mark_b);
                t.setVisibility(View.INVISIBLE);

                break;

            default:
        }
    }

// ******************************************************************************

    public void setupGameVariables() {

        int no_sets = myDb.readSystem(DBAdapter.KEY_SYSTEM_NO_SETS);
        set_Type = myDb.readSystem(DBAdapter.KEY_SYSTEM_SET_TYPE);  // 0-AdvSet  1-TbSet
        last_Set_Type = myDb.readSystem(DBAdapter.KEY_SYSTEM_LAST_SET);  // 0-AdvSet  1-TbSet

        fast4 = myDb.readSystem(DBAdapter.KEY_SYSTEM_FAST4);
        no_adv = myDb.readSystem(DBAdapter.KEY_SYSTEM_NO_ADV);
        short_sets = myDb.readSystem(DBAdapter.KEY_SYSTEM_SHORT_SETS);
        match_tb_game = myDb.readSystem(DBAdapter.KEY_SYSTEM_MATCH_TB);
        mtb_7 = myDb.readSystem(DBAdapter.KEY_SYSTEM_MTB_7);
        mtb_10 = myDb.readSystem(DBAdapter.KEY_SYSTEM_MTB_10);

        if (no_sets == 1) {
            no_Sets = 1;
            setsToWinPerPlayer = 1;
            nextToLastSet = 0;
            last_Set = true;
        }

        if (no_sets == 3) {
            no_Sets = 3;
            setsToWinPerPlayer = 2;
            nextToLastSet = 1;
        }

        if (no_sets == 5) {
            no_Sets = 5;
            setsToWinPerPlayer = 3;
            nextToLastSet = 2;
        }

        if (short_sets == 1) {
            minGamesToWinSet = 4;
        } else {
            minGamesToWinSet = 6;
        }

        noAdv = (myDb.readSystem(DBAdapter.KEY_SYSTEM_NO_ADV) == 1);

        set_Type = myDb.readSystem(DBAdapter.KEY_SYSTEM_SET_TYPE);

        if (set_Type == 0) {
            advLastSet = (myDb.readSystem(DBAdapter.KEY_SYSTEM_LAST_SET) == 0);
        }
    }

// ******************************************************************************

    public void pointsPlus_a() {
        if (!matchComplete) {
            if (setServer()) {
                Buttons_Off("H");
                history();

                c_points_h++;
                points();
            }
        }
    }

// ******************************************************************************

    public void pointsPlus_b() {
        if (!matchComplete) {
            if (setServer()) {
                Buttons_Off("V");
                history();

                c_points_v++;
                points();
            }
        }
    }

// ******************************************************************************

    public void onClick_pointsPlus_a(View view) {
        if (!matchComplete) {
            if (setServer()) {
                Buttons_Off("H");
                history();

                c_points_h++;
                points();
            }
        }
    }

// ******************************************************************************

    public void onClick_pointsNeg_a(View view) {
        if (!matchComplete) {
            if (setServer()) {
                t = (TextView) findViewById(id.id_b_pointsNeg_a);
                t.setVisibility(View.INVISIBLE);

                restore();
                points();
            }
        }
    }

// ******************************************************************************

    public void onClick_pointsPlus_b(View view) {
        if (!matchComplete) {
            if (setServer()) {
                Buttons_Off("V");
                history();

                c_points_v++;
                points();
            }
        }
    }

// ******************************************************************************

    public void onClick_pointsNeg_b(View view) {
        if (!matchComplete) {
            if (setServer()) {

                t = (TextView) findViewById(id.id_b_pointsNeg_b);
                t.setVisibility(View.INVISIBLE);

                restore();
                points();
            }
        }
    }

// ******************************************************************************

    private void points() {

        if (c_points_h < 0) c_points_h = 0;
        if (c_points_v < 0) c_points_v = 0;

        if (game_Type == 0) {
            points_Adv();
        } else {
            points_Tb();
        }

        c_audio = c_audio_temp;
        c_audio_temp = "X";

        if (matchComplete) {
            c_points_h = 0;
            c_points_v = 0;

            c_games_h = 0;
            c_games_v = 0;

            c_sets_h = 0;
            c_sets_v = 0;
        }

        myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_POINTS_A, String.valueOf(c_points_h));
        myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_POINTS_B, String.valueOf(c_points_v));

        myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_GAMES_A, String.valueOf(c_games_h));
        myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_GAMES_B, String.valueOf(c_games_v));

        myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_SETS_A, String.valueOf(c_sets_h));
        myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_SETS_B, String.valueOf(c_sets_v));

        onResume();
    }

// ******************************************************************************

    private void points_Adv() {

        // Normal Game

        int[][] res_arr = {{0, 0, 0, 0, 1, 5}, {0, 0, 0, 0, 1, 5}, {0, 0, 0, 0, 1, 5}, {0, 0, 0, 2, 3, 1}, {1, 1, 1, 6, 4, 5}, {5, 5, 5, 1, 5, 5}};

        // 0 - Add to score
        // 1 - Win game
        // 2 - Deuce
        // 3 - Advantage B
        // 4 - Back to deuce
        // 5 - No action
        // 6 - Advantage A

        int result = res_arr[c_points_h][c_points_v];

        switch (result) {
            case 0:
            case 5:
                break;
            case 1:
                Win_Adv_Game();
                break;
            case 2:
                Deuce();
                break;
            case 3:
                if (noAdv) {
                    Win_Adv_Game();
                } else {
                    Advantage_B();
                }
                break;
            case 4:
                Back_to_Deuce();
                break;
            case 6:
                if (noAdv) {
                    Win_Adv_Game();
                } else {
                    Advantage_A();
                }
                break;
        }
    }

// ******************************************************************************

    private void Win_Adv_Game() {

        if (c_points_h > c_points_v) {
            c_games_h++;
        } else {
            c_games_v++;
        }
        c_points_h = 0;
        c_points_v = 0;

        Intent intent = new Intent(MainActivity.this, GameSplashActivity.class);
        startActivity(intent);

        L.d("Game_Adv");

        c_audio_temp = "G";

        flip_server();

        set_Adv();

        Next_Game();
    }

// ******************************************************************************

    private void set_Adv() {

        // Advantage Set

        if ((c_games_h < minGamesToWinSet) && (c_games_v < minGamesToWinSet)) {
            return;
        }

        if ((c_games_h >= minGamesToWinSet) && (c_games_v <= (c_games_h - 2))) {
            c_sets_h++;

            set_Results();

            c_games_h = 0;
            c_games_v = 0;

            if (!Match()) {
                Intent intent = new Intent(MainActivity.this, SetSplashActivity.class);
                startActivity(intent);

                L.d("Sets Adv");

                c_audio_temp = "S";
            }
        }

        if ((c_games_v >= minGamesToWinSet) && (c_games_h <= (c_games_v - 2))) {
            c_sets_v++;

            set_Results();

            c_games_h = 0;
            c_games_v = 0;

            if (!Match()) {
                Intent intent = new Intent(MainActivity.this, SetSplashActivity.class);
                startActivity(intent);

                L.d("Sets Adv");

                c_audio_temp = "S";
            }
        }
    }

    // ******************************************************************************

    private void points_Tb() {

        // Tie Break Game

        if (((c_points_h >= 7) && (c_points_v <= (c_points_h - 2))) | ((c_points_v >= 7) && (c_points_h <= (c_points_v - 2)))) {    // Is it a game?

            if (c_points_h > c_points_v) {
                c_games_h++;
            } else {
                c_games_v++;
            }

            if ((c_games_h > c_games_v)) {
                c_sets_h++;
            } else {
                c_sets_v++;
            }

            set_Results();

            if (last_Set) {
                L.d("Match");

                Match();

                c_points_h = 0;
                c_points_v = 0;

                c_games_h = 0;
                c_games_v = 0;

                c_sets_h = 0;
                c_sets_v = 0;

                return;
            } else {
                Intent intent = new Intent(MainActivity.this, SetSplashActivity.class);
                startActivity(intent);

                L.d("Sets Tb");

                c_audio_temp = "S";
            }

            if ((c_points_h >= 7) & (c_points_v <= (c_points_h - 2))) {
                L.d("Game Tb Home");
            }else{
                L.d("Game Tb Visitor");
            }

            c_points_h = 0;
            c_points_v = 0;

            c_games_h = 0;
            c_games_v = 0;

            flipFlag = false;
            flipCntr = 2;

            Next_Game();

            c_points_h = 0;
            c_points_v = 0;

            c_games_h = 0;
            c_games_v = 0;

        } else {
            if (!flipFlag) {
                flipFlag = true;

                flip_server();
            } else {
                flipCntr--;
                if (flipCntr == 0) {
                    flipCntr = 2;

                    flip_server();

                    c_audio_temp = "C";
                }
            }
        }
    }

// ******************************************************************************

    private boolean Match() {

        if (c_sets_h == setsToWinPerPlayer || c_sets_v == setsToWinPerPlayer) {

            L.d("Match");

            c_audio_temp = "M";

            AllButtonsOff();

            matchComplete = true;

            Intent intent = new Intent(MainActivity.this, ResultsActivity.class);
            startActivity(intent);

            intent = new Intent(MainActivity.this, MatchSplashActivity.class);
            startActivity(intent);

//            t = (TextView) findViewById(id.id_advantage);
//            t.setVisibility(View.INVISIBLE);
//            t = (TextView) findViewById(id.id_tie_break);
//            t.setVisibility(View.INVISIBLE);
//            t = (TextView) findViewById(id.id_reset);
//            t.setVisibility(View.VISIBLE);

            return true;
        }
        return false;
    }

// ******************************************************************************

    private void Next_Game() {
        if (game_Type == 1) {
            game_Type = 0;
            GameNotice(0);          // Advantage Game

            return;
        }

        if (set_Type == 1) {
            GameNotice(0);          // Advantage Game
        } else {
            if ((c_games_h == minGamesToWinSet) && (c_games_v == minGamesToWinSet)) {   // Set "Deuce"
                if ((c_sets_h == nextToLastSet) || (c_sets_v == nextToLastSet)) {       // Last Set
                    last_Set = true;

                    if (advLastSet) {
                        GameNotice(0);  // Advantage Game
                    } else {
                        game_Type = 1;
                        GameNotice(1);   // Tie Break
                    }
                } else {
                    game_Type = 1;
                    GameNotice(1);   // Tie Break
                }
            } else {
                GameNotice(0);          // Advantage Game
            }
        }
    }

    // ******************************************************************************

    private void GameNotice(int type) {             //  0 - Advantage    1 - Tie Break    2 - Reset
        switch (type) {
            case 0: // Advantage
                t = (TextView) findViewById(id.id_notice);
                t.setText(R.string.advantage_game);
                break;

            case 1: // Tie Break
                t = (TextView) findViewById(id.id_notice);
                t.setText(R.string.tie_break_game);
                break;

            case 2: // Reset
                t = (TextView) findViewById(id.id_notice);
                t.setText(R.string.reset);
                break;



//                    <string name="tie_break_game">Tie Break Game</string>
//    <string name="advantage_game">Advantage Game</string>
//
//
//                        <!--        android:text="@string/tie_break_game"-->
//        <!--            android:text="@string/advantage_game"-->
//        <!--            android:text="Reset"-->
//
//
//
//
//
//
//
//
//                        <string name="tie_break_game">Tie Break Game</string>
//    <string name="advantage_game">Advantage Game</string>
//
//                //                t.setVisibility(View.VISIBLE);
////
////                t = (TextView) findViewById(id.id_tie_break);
////                t.setVisibility(View.INVISIBLE);
////
////                t = (TextView) findViewById(id.id_reset);
////                t.setVisibility(View.INVISIBLE);
//
//            case 1: // Tie Break
//                t = (TextView) findViewById(id.id_advantage);
//                t.setVisibility(View.INVISIBLE);
//
//                t = (TextView) findViewById(id.id_tie_break);
//                t.setVisibility(View.VISIBLE);
//
//                t = (TextView) findViewById(id.id_reset);
//                t.setVisibility(View.INVISIBLE);
//
//            case 2: // Reset
//                t = (TextView) findViewById(id.id_advantage);
//                t.setVisibility(View.INVISIBLE);
//
//                t = (TextView) findViewById(id.id_tie_break);
//                t.setVisibility(View.INVISIBLE);
//
//                t = (TextView) findViewById(id.id_reset);
//                t.setVisibility(View.VISIBLE);

        }
    }

// ******************************************************************************

    private void set_Results() {

        int tot_sets = c_sets_h + c_sets_v;

        switch (tot_sets) {
            case 1:
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_1_H, c_games_h);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_1_V, c_games_v);

                break;

            case 2:
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_2_H, c_games_h);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_2_V, c_games_v);

                break;

            case 3:
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_3_H, c_games_h);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_3_V, c_games_v);

                break;

            case 4:
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_4_H, c_games_h);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_4_V, c_games_v);

                break;

            case 5:
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_5_H, c_games_h);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_5_V, c_games_v);

                break;

            default:
        }
    }

// ******************************************************************************

    private void Advantage_A() {

        Intent intent = new Intent(MainActivity.this, AdvSplashActivity.class);
        startActivity(intent);

        L.d("Adv");

        if (server.equals("H")) {
            c_audio_temp = "A";
        } else {
            c_audio_temp = "a";
        }
    }

// ******************************************************************************

    private void Advantage_B() {

        Intent intent = new Intent(MainActivity.this, AdvSplashActivity.class);
        startActivity(intent);

        L.d("Adv");

        if (server.equals("V")) {
            c_audio_temp = "A";
        } else {
            c_audio_temp = "a";
        }
    }

// ******************************************************************************

    private void Deuce() {

        Intent intent = new Intent(MainActivity.this, DeuceSplashActivity.class);
        startActivity(intent);

        L.d("Deuce");

        c_audio_temp = "D";
    }

// ******************************************************************************

    private void Back_to_Deuce() {

        Intent intent = new Intent(MainActivity.this, DeuceSplashActivity.class);
        startActivity(intent);

        L.d("Back to Deuce");

        c_audio_temp = "D";

        c_points_h--;
        c_points_v--;
    }

    // ******************************************************************************

    public boolean setServer() {
        if ("Z".equals(server)) {
            Toast toast = Toast.makeText(getApplicationContext(), "Cannot score " +
                    "until 1st. Server is set.", Toast.LENGTH_SHORT);
            toast.show();

            return false;
        }
        return true;
    }

// ******************************************************************************

    public void Buttons_Off(String player) {

        strPlayerButton = player;

        start_ButtonTimer();

        t = (TextView) findViewById(id.id_b_pointsPlus_a);
        t.setVisibility(View.INVISIBLE);

        t = (TextView) findViewById(id.id_b_pointsPlus_b);
        t.setVisibility(View.INVISIBLE);

        t = (TextView) findViewById(id.id_b_pointsNeg_a);
        t.setVisibility(View.INVISIBLE);

        t = (TextView) findViewById(id.id_b_pointsNeg_b);
        t.setVisibility(View.INVISIBLE);
    }

// ******************************************************************************

    public void AllButtonsOff() {

        stop_ButtonTimer();

        t = (TextView) findViewById(id.id_b_pointsPlus_a);
        t.setVisibility(View.INVISIBLE);

        t = (TextView) findViewById(id.id_b_pointsPlus_b);
        t.setVisibility(View.INVISIBLE);

        t = (TextView) findViewById(id.id_b_pointsNeg_a);
        t.setVisibility(View.INVISIBLE);

        t = (TextView) findViewById(id.id_b_pointsNeg_b);
        t.setVisibility(View.INVISIBLE);
    }

// ******************************************************************************

    public void ButtonsOn() {

        t = (TextView) findViewById(id.id_b_pointsPlus_a);
        t.setVisibility(View.VISIBLE);

        t = (TextView) findViewById(id.id_b_pointsPlus_b);
        t.setVisibility(View.VISIBLE);

        switch (strPlayerButton) {
            case "H":
                t = (TextView) findViewById(id.id_b_pointsNeg_a);
                t.setVisibility(View.VISIBLE);
                break;
            case "V":
                t = (TextView) findViewById(id.id_b_pointsNeg_b);
                t.setVisibility(View.VISIBLE);
        }
    }

// ******************************************************************************

    private void history() {

        h_points_h = c_points_h;
        h_points_v = c_points_v;
        h_games_h = c_games_h;
        h_games_v = c_games_v;
        h_sets_h = c_sets_h;
        h_sets_v = c_sets_v;

        h_server = server;
    }

// ******************************************************************************

    private void restore() {
        c_points_h = h_points_h;
        c_points_v = h_points_v;
        c_games_h = h_games_h;
        c_games_v = h_games_v;
        c_sets_h = h_sets_h;
        c_sets_v = h_sets_v;

        server = h_server;

        myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_SERVER, server);
    }

// ******************************************************************************

    private void resetAlertDialog() {

        Context context = MainActivity.this;
        String message = "You REALLY want to Reset everything?";
        String button1String = "Reset";
        String button2String = "Cancel";
        AlertDialog.Builder ad = new AlertDialog.Builder(context);
        ad.setMessage(message);
        ad.setPositiveButton(button1String, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int arg1) {

                myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_NAME_A, "Home");
                myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_NAME_B, "Visitors");

        // Reset scores
                myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_POINTS_A, "0");
                myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_POINTS_B, "0");
                myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_GAMES_A, "0");
                myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_GAMES_B, "0");
                myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_SETS_A, "0");
                myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_SETS_B, "0");

        // Reset Results
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_1_H, 0);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_1_V, 0);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_2_H, 0);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_2_V, 0);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_3_H, 0);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_3_V, 0);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_4_H, 0);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_4_V, 0);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_5_H, 0);
                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_5_V, 0);

//                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_NO_1, 0);
//                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_NO_3, 0);
//                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_NO_5, 0);
//
//                myDb.updateSystem(DBAdapter.KEY_SYSTEM_NO_ADV, 0);
//                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SET_TYPE, 0);
//                myDb.updateSystem(DBAdapter.KEY_SYSTEM_LAST_SET, 0);
//                myDb.updateSystem(DBAdapter.KEY_SYSTEM_NO_ADV, 0);
//                myDb.updateSystem(DBAdapter.KEY_SYSTEM_FAST4, 0);
//                myDb.updateSystem(DBAdapter.KEY_SYSTEM_SHORT_SETS, 0);
//                myDb.updateSystem(DBAdapter.KEY_SYSTEM_MATCH_TB, 0);
//                myDb.updateSystem(DBAdapter.KEY_SYSTEM_MTB_7, 0);
//                myDb.updateSystem(DBAdapter.KEY_SYSTEM_MTB_10, 0);

                t = (TextView) findViewById(id.id_b_pointsNeg_a);
                t.setVisibility(View.INVISIBLE);
                t = (TextView) findViewById(id.id_b_pointsNeg_b);
                t.setVisibility(View.INVISIBLE);

        // Reset Server
                t = (TextView) findViewById(id.id_server_mark_a);
                t.setVisibility(View.INVISIBLE);
                t = (TextView) findViewById(id.id_server_mark_b);
                t.setVisibility(View.INVISIBLE);

                server = "Z";
                myDb.updateSystemStr(DBAdapter.KEY_SYSTEM_SERVER, server);

                strPlayerButton = "Z";
                ButtonsOn();

                t = (TextView) findViewById(id.id_set_server);
                t.setVisibility(View.VISIBLE);

                flipFlag = false;
                flipCntr = 2;

                game_Type = 0;
                matchComplete = false;
                last_Set = false;

//                GameNotice(false);

                Intent s_intent = new Intent(MainActivity.this, RulesActivity.class);
                startActivity(s_intent);

                myDb.K_Log("Reset Yes");

                onResume();
            }
        });
        ad.setNegativeButton(button2String, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int arg1) {
                // do nothing
                myDb.K_Log("Reset No");
            }
        });
        ad.show();
    }

// ******************************************************************************

    private void quitAlertDialog() {

        Context context = MainActivity.this;
        String message = "You REALLY want to Quit?";
        String button1String = "Quit";
        String button2String = "Cancel";
        AlertDialog.Builder ad = new AlertDialog.Builder(context);
        ad.setMessage(message);
        ad.setPositiveButton(button1String, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int arg1) {

                myDb.K_Log("Quit App");
                finish();
            }
        });
        ad.setNegativeButton(button2String, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int arg1) {
                // do nothing
                myDb.K_Log("Quit Not");
            }
        });
        ad.show();
    }

// ******************************************************************************
// Timer to delay running onResume on start up
// ******************************************************************************

    public void start_ResumeTimer() {

        if (!res_timerActive) {
            res_timerActive = true;
            res_counter = new resumeTimer(500, 500);
            res_counter.start();
        }
    }

// ******************************************************************************

    private class resumeTimer extends CountDownTimer {

        private resumeTimer(long res_millisInFuture, long countDownInterval) {

            super(res_millisInFuture, countDownInterval);
        }

        // ******************************************************************************

        @Override
        public void onFinish() {

            onResume();
            res_counter = null;
            res_timerActive = false;
        }

        // ******************************************************************************

        @Override
        public void onTick(long millisUntilFinished) {

        }
    }

// ******************************************************************************
// Timer to show scoring buttons after a delay
// ******************************************************************************

    public void start_ButtonTimer() {

        button_counter = new buttonTimer(500, 500);
        button_counter.start();
    }

// ******************************************************************************

    public void stop_ButtonTimer() {

        if (button_counter != null) {
            button_counter.cancel();
        }
    }

// ******************************************************************************

    private class buttonTimer extends CountDownTimer {

        buttonTimer(long button_millisInFuture, long countDownInterval) {

            super(button_millisInFuture, countDownInterval);
        }

        // ******************************************************************************

        @Override
        public void onFinish() {

            ButtonsOn();
            button_counter = null;
        }

        // ******************************************************************************

        @Override
        public void onTick(long millisUntilFinished) {

        }
    }

// ******************************************************************************
// Timer for repetitive battery level tests
// ******************************************************************************

    public void start_batteryTimer() {

        if (battery_counter != null) {
            battery_counter.cancel();
        }

        battery_counter = new batteryTimer(30000, 30000);
        battery_counter.start();
    }

// ******************************************************************************

    public void stop_batteryTimer() {

        if (battery_counter != null) {
            battery_counter.cancel();
        }
    }

// ******************************************************************************

    private class batteryTimer extends CountDownTimer {

        batteryTimer(long battery_millisInFuture, long countDownInterval) {

            super(battery_millisInFuture, countDownInterval);
        }

        // ******************************************************************************

        @Override
        public void onFinish() {

            battery_counter = null;
            start_batteryTimer();
            /*
              Computes the battery level by registering a receiver to the intent triggered
              by a battery status/level change.
             */
            BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {

                    context.unregisterReceiver(this);
                    int rawlevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int level;
                    if (rawlevel >= 0 && scale > 0) {
                        level = (rawlevel * 100) / scale;
                        checkBattery(level);
                    }
                }
            };
            IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            registerReceiver(batteryLevelReceiver, batteryLevelFilter);
        }

        // ******************************************************************************

        @Override
        public void onTick(long millisUntilFinished) {
        }
    }

// ******************************************************************************

    private void checkBattery(int level) {

        String mess = "";
        if (level <= batteryTestLevel) {
            batteryTestLevel = batteryTestLevel - getInc();
            switch (batteryMessageNo) {
                case 5:
                    mess = "The Battery is getting low.";
                    break;
                case 4:
                    mess = "The Battery is getting lower.";
                    break;
                case 3:
                    mess = "The Battery is getting lower and lower.";
                    break;
                case 2:
                    mess = "The Battery is getting very low.";
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = 20 / 100.0f;
                    getWindow().setAttributes(lp);
                    break;
                case 1:
                    mess = "Scorer will SHUT DOWN in 30 seconds";
                    batteryTestLevel = batteryTestLevel + getInc();
                    break;
                case 0:
                    android.os.Process.killProcess(Process.myPid());
                    System.exit(0);
                    finish();
                    break;
            }
            myDb.K_Log("Battery Message - " + mess);
            batteryAlertDialog(mess, Integer.toString(batteryMessageNo));
            batteryMessageNo--;
        }
    }

// ******************************************************************************

    private void batteryAlertDialog(String message, String no) {

        if (ad != null) {
            ad.cancel();
            Alarm.stopAlarm();
        }
        Alarm.soundAlarm(context);
        myDb.K_Log(message + "  " + no);
        Context context = MainActivity.this;
        String button1String = "Acknowledge";
        AlertDialog.Builder adb = new AlertDialog.Builder(context);
        adb.setMessage(message);
        adb.setTitle(no);
        adb.setPositiveButton(button1String, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int arg1) {

                Alarm.stopAlarm();
            }
        });
        ad = adb.create();
        ad.show();
    }


// ***************************WiFiWiFiWiFiWiFiWiFiWiFi*********************************************
// WiFi
// ***************************WiFiWiFiWiFiWiFiWiFiWiFi*********************************************

    private boolean checkWiFiEnabled() {
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);

        if (wifi != null) {
            if (!wifi.isWifiEnabled()) {
                t = (TextView) findViewById(id.id_wifi_message);
                t.setTextColor(ContextCompat.getColor(this, R.color.red));
                t.setText(R.string.wifi_disabled);

                t = (TextView) findViewById(id.id_wifi_connected);
                t.setTextColor(ContextCompat.getColor(this, R.color.red));
                t.setText(R.string.wifi_not_connected);

                wifi.setWifiEnabled(true);
                wifiEnabled = false;

                return false;
            } else {
                t = (TextView) findViewById(id.id_wifi_message);
                t.setTextColor(ContextCompat.getColor(this, R.color.dk_green));
                t.setText(R.string.wifi_enabled);
                wifiEnabled = true;

                return true;
            }
        }
        return false;
    }
    // ******************************************************************************

    private boolean checkWiFiConnected() {
        final WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);

        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = null;
        if (cm != null) {
            networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        }
        WifiInfo wifiInfo = null;
        if (wifi != null) {
            wifiInfo = wifi.getConnectionInfo();
        }

        assert networkInfo != null;
        assert wifiInfo != null;
        if (!(networkInfo.isConnected() && wifiInfo.getSSID().replace("\"", "").equals(WifiSSID))) {
            t = (TextView) findViewById(id.id_wifi_connected);
            t.setTextColor(ContextCompat.getColor(this, R.color.red));
            t.setText(R.string.wifi_not_connected);
            stopSendThread = true;
            stopRecvThread = true;

            sendThread = null;
            receiveThread = null;

            stop_flashTimer();

            wifiConnected = false;

            return false;
        } else {
            t = (TextView) findViewById(id.id_wifi_connected);
            t.setTextColor(ContextCompat.getColor(this, R.color.dk_green));
            t.setText(R.string.wifi_connected);
            stopSendThread = false;
            stopRecvThread = false;

            if (sendThread == null) {
                sendThread = new SendThread();
                new Thread(sendThread).start();
            }
            if (receiveThread == null) {
                receiveThread = new ReceiveThread();
                new Thread(receiveThread).start();
            }

            start_flashTimer();

            wifiConnected = true;

            return true;
        }
    }

// ******************************************************************************

    /**
     * Start to connect to a specific wifi network
     */
    private void connectToSpecificNetwork() {
        if (!connect_in_progress) {

            final WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);

            ScanReceiver scanReceiver = new ScanReceiver();
            registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

            if (wifi != null) {
                wifi.startScan();
            }

            connect_in_progress = true;
        }
    }

// ******************************************************************************

    /**
     * Broadcast receiver for wifi scanning related events
     */
    private class ScanReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
            List<ScanResult> scanResultList = null;
            if (wifi != null) {
                scanResultList = wifi.getScanResults();
            }
            boolean found = false;
            if (scanResultList != null) {
                for (ScanResult scanResult : scanResultList) {
                    if (scanResult.SSID.equals(WifiSSID)) {
                        found = true;
                        break;                  // found don't need continue
                    }
                }
            }
            if (!found) {
                unregisterReceiver(ScanReceiver.this);
                connect_in_progress = false;

            } else {
                // configure based on security
                final WifiConfiguration conf = new WifiConfiguration();
                conf.SSID = "\"" + WifiSSID + "\"";

//                String SS = padLeftZero("" + getSSID(), 2);
                String SS = "" + getSSID();

                String wifiPass = "1" + SS + "2" + SS + "3" + SS + "4";
                conf.preSharedKey = "\"" + wifiPass + "\"";

                int netId = wifi.addNetwork(conf);
                wifi.disconnect();
                wifi.enableNetwork(netId, true);
                wifi.reconnect();

                unregisterReceiver(this);
            }
        }
    }

// ******************************************************************************

    private void wifi_enable_off() {
        t = (TextView) findViewById(id.id_wifi_message);
        t.setTextColor(ContextCompat.getColor(this, R.color.red));
        t.setText(R.string.wifi_disabled);

        t = (TextView) findViewById(id.id_wifi_connected);
        t.setTextColor(ContextCompat.getColor(this, R.color.red));
        t.setText(R.string.wifi_not_connected);
    }

// ******************************************************************************
//      Send Thread
// ******************************************************************************

    private class SendThread implements Runnable {
        SendThread() {
//        puts the thread in the background
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        }

        @Override
        public void run() {

            try {
                socket = new Socket("1.2.3.4", 2000);
            } catch (IOException e) {
                e.printStackTrace();
            }

            sleep(1000);

            while (!stopSendThread) {
                if (wifiEnabled) {
                    if (wifiConnected) {

                        try {
                            dataOut = new DataOutputStream(socket.getOutputStream());
                            dataOut.writeUTF(dataString());
                        } catch (IOException ignored) {
                        }

                        sleep(1000);
                    } else {
                        sleep(1000);
                    }
                } else {
                    sleep(1000);
                }
            }

            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (dataOut == null) {
                try {
                    dataOut.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

// ******************************************************************************
//      Receive Thread
// ******************************************************************************

    private class ReceiveThread implements Runnable {
//        ReceiveThread() {
////        puts the thread in the background
//            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
//        }

        public void run() {

            char[] buff = new char[4];

            while (true) {
                if (!(socket == null)) break;
            }

            try {
                dataIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }

            while (!stopRecvThread) {
                try {
                    dataIn.read(buff, 0, 4);

                    final String result = new String(buff);

                    if (result.startsWith("GJC")) {
                        String recvResult = result.substring(3, 4);

                        if (recvResult.equals("0")) {
                            stopRecv = false;
                        }
                        if (recvResult.equals("3") && !stopRecv) {
                            stopRecv = true;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pointsPlus_b();
                                }
                            });
                        }
                        if (recvResult.equals("2") && !stopRecv) {
                            stopRecv = true;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pointsPlus_a();
                                }
                            });
                        }
                    }
                } catch (IOException ignored) {
                }

                sleep(500);
            }

            if (socket != null) {
                try {
                    socket.close();
                    socket = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (dataIn == null) {
                try {
                    dataIn.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

// ******************************************************************************

    private String dataString() {
        String s1;
        if (getStartTesting()) {
            if (getTest()) {


                setTest(false);
                s1 = "GJCXXXZ8888888888";
            } else {
                setTest(true);
                s1 = "GJCXXXZBBBBBBBBBB";
            }
        } else {
            s1 = "GJCXX"                    //+ "Z8888888888";
                    + c_audio
                    + server
                    + c_sets_v
                    + padLeftZero(c_games_v, 2)
                    + convert_Points_Transmit(c_points_v)
                    + c_sets_h
                    + padLeftZero(c_games_h, 2)
                    + convert_Points_Transmit(c_points_h);

            c_audio = "X";
        }
        return s1;
    }

// ******************************************************************************
// Timer to check WiFi
// ******************************************************************************

    public void start_wifiTimer() {
        if (wifi_counter != null) {
            wifi_counter.cancel();
        }

        wifi_counter = new wifiTimer(2000, 1000);
        wifi_counter.start();
    }

// ******************************************************************************

    public void stop_wifiTimer() {

        if (wifi_counter != null) {
            wifi_counter.cancel();
        }
    }

// ******************************************************************************

    private class wifiTimer extends CountDownTimer {

        wifiTimer(long wifi_millisInFuture, long countDownInterval) {

            super(wifi_millisInFuture, countDownInterval);
        }

        // ******************************************************************************

        @Override
        public void onFinish() {
            if (checkWiFiEnabled()) {
                if (checkWiFiConnected()) {

                    connect_in_progress = false;

                } else {
                    connectToSpecificNetwork();
                }
            } else {
                wifi_enable_off();
            }
            wifi_counter = null;
            start_wifiTimer();
        }

        // ******************************************************************************

        @Override
        public void onTick(long millisUntilFinished) {
        }
    }

// ******************************************************************************
// Timer for flashing WiFi Box Connected
// ******************************************************************************

    private boolean flash = true;

    public void start_flashTimer() {

        if (flash_counter != null) {
            return;
        }
        flash_counter = new flashTimer(250, 250);
        flash_counter.start();

        flash = !flash;
    }

// ******************************************************************************

    public void stop_flashTimer() {

        if (flash_counter != null) {
            flash_counter.cancel();
            flash_counter = null;

            i = (ImageView) findViewById(R.id.id_wifi_dot);
            i.setVisibility(View.INVISIBLE);
        }
    }

// ******************************************************************************

    private class flashTimer extends CountDownTimer {
        flashTimer(long flash_millisInFuture, long countDownInterval) {
            super(flash_millisInFuture, countDownInterval);
        }

        @Override
        public void onFinish() {
            flash_counter = null;

            if (flash) {
                i = (ImageView) findViewById(R.id.id_wifi_dot);
                i.setVisibility(View.VISIBLE);
            } else {
                i = (ImageView) findViewById(R.id.id_wifi_dot);
                i.setVisibility(View.INVISIBLE);
            }
            start_flashTimer();
        }

        @Override
        public void onTick(long millisUntilFinished) {
        }

    }

// ******************************************************************************
// Timer for flashing ILLEGAL title
// ******************************************************************************

    private boolean illegal_flash = true;

    public void start_illegal_flashTimer() {

        if (illegal_flash_counter != null) {
            return;
        }
        illegal_flash_counter = new illegal_flashTimer(500, 500);
        illegal_flash_counter.start();

        illegal_flash = !illegal_flash;
    }

// ******************************************************************************

    public void stop_illegal_flashTimer() {

        if (illegal_flash_counter != null) {
            illegal_flash_counter.cancel();
            illegal_flash_counter = null;

            t = (TextView) findViewById(R.id.id_club);
            t.setVisibility(View.INVISIBLE);
        }
    }

// ******************************************************************************

    private class illegal_flashTimer extends CountDownTimer {
        illegal_flashTimer(long illegal_flash_millisInFuture, long countDownInterval) {
            super(illegal_flash_millisInFuture, countDownInterval);
        }

        @Override
        public void onFinish() {
            illegal_flash_counter = null;

            if (illegal_flash) {
                t = (TextView) findViewById(R.id.id_club);
                t.setVisibility(View.VISIBLE);
            } else {
                t = (TextView) findViewById(R.id.id_club);
                t.setVisibility(View.INVISIBLE);
            }
            start_illegal_flashTimer();
        }

        @Override
        public void onTick(long millisUntilFinished) {
        }
    }

// ******************************************************************************
// Timer for flashing Set Server message
// ******************************************************************************

    private boolean server_flash = true;

    public void start_server_flashTimer() {

        if (server_flash_counter != null) {
            return;
        }
        server_flash_counter = new server_flashTimer(500, 500);
        server_flash_counter.start();

        server_flash = !server_flash;
    }

// ******************************************************************************

    public void stop_server_flashTimer() {

        if (server_flash_counter != null) {
            server_flash_counter.cancel();
            server_flash_counter = null;
        }

        t = (TextView) findViewById(R.id.id_set_server);
        t.setVisibility(View.INVISIBLE);
    }

// ******************************************************************************

    private class server_flashTimer extends CountDownTimer {
        server_flashTimer(long server_flash_millisInFuture, long countDownInterval) {
            super(server_flash_millisInFuture, countDownInterval);
        }

        @Override
        public void onFinish() {
            server_flash_counter = null;

            if (server_flash) {
                t = (TextView) findViewById(R.id.id_set_server);
                t.setVisibility(View.VISIBLE);
            } else {
                t = (TextView) findViewById(R.id.id_set_server);
                t.setVisibility(View.INVISIBLE);
            }
            start_server_flashTimer();
        }

        @Override
        public void onTick(long millisUntilFinished) {
        }

    }

    // ******************************************************************************

    public static String padLeftZero(int s, int n) {

        return String.format("%0" + n + "d", Integer.parseInt(String.valueOf(s)));
    }
}
