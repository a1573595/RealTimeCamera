package com.a1573595.realtimecamera;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.asus.robotframework.API.MotionControl;
import com.asus.robotframework.API.RobotAPI;
import com.asus.robotframework.API.RobotFace;

public class MainActivity extends AppCompatActivity {
    private ListView listView;
    private String[] features = new String[]{"ObjectDetection", "GunBinaryClassification",
            "LogoClassification"};

    private RobotAPI robotAPI;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        robotAPI = new RobotAPI(getApplicationContext());
        robotAPI.robot.setPressOnHeadAction(false); //disable press on head action
        robotAPI.robot.setTouchOnlySignal(false);   //disable "touch only" on top of screen if user trigger by "Hey Zenbo"
        robotAPI.robot.setVoiceTrigger(false);  //disable dialog system voice trigger
        robotAPI.motion.moveHead(0,30, MotionControl.SpeedLevel.Head.L1);
        robotAPI.robot.setExpression(RobotFace.HIDEFACE);

        listView = findViewById(R.id.listview);
        listView.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, features));
        listView.setOnItemClickListener((adapterView, view, position, id) -> {
            switch (position) {
                case 0:
                    startActivity(new Intent(this, ObjectDetectionActivity.class));
                    break;
                case 1:
                    startActivity(new Intent(this, BinaryClassificationActivity.class));
                    break;
                case 2:
                    startActivity(new Intent(this, LogoClassificationActivity.class));
                    break;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(robotAPI!=null)
            robotAPI.release();
    }
}
