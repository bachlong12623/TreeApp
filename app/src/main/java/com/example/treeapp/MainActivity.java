package com.example.treeapp;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.treeapp.databinding.ActivityMainBinding;


import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "WaterReminder";
    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private FusedLocationProviderClient fusedLocationClient;

    private String[] treeNames;
    private String[] treeCodes;
    private String[] wikiLinks;
    private String[] otherLinks;
    private RecyclerView recyclerView;
    private TreeListAdapter treeListAdapter;
    private DatabaseHelper dbHelper;
    WeatherData weatherData;

    // Create a lenient DateTimeFormatter that accepts single-digit days and months
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d/M/yyyy");



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Request location access permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getLocation();
        }

        dbHelper = new DatabaseHelper(this);
        dbHelper.initializeDatabase();

        // Open the database
        dbHelper.openDatabase();

//         Load data from the database and update the UI
        loadDataFromDatabase();


        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showTreeListDialog();
            }
        });
        scheduleDailyRainfallCheck();
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        recyclerView = findViewById(R.id.recycler_view_trees);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        treeListAdapter = new TreeListAdapter(this, null); // Pass context and null data initially
        recyclerView.setAdapter(treeListAdapter);

        // Load data from Room database
        loadTreeData();
    }

    private void loadDataFromDatabase() {
        // Get readable database
        SQLiteDatabase db = dbHelper.getDatabase();

        // Query to fetch the data
        String query = "SELECT * FROM tree_read";
        Cursor cursor = db.rawQuery(query, null);

        // Initialize ArrayLists to store the data
        List<String> treeNameList = new ArrayList<>();
        List<String> treeCodeList = new ArrayList<>();
        List<String> wikiLinkList = new ArrayList<>();
        List<String> otherLinkList = new ArrayList<>();

        // Loop through the results
        if (cursor.moveToFirst()) {
            do {
                // Add data to ArrayLists
                treeCodeList.add(cursor.getString(cursor.getColumnIndex("id"))); // treeCode
                treeNameList.add(cursor.getString(cursor.getColumnIndex("name")));  // treeName
                wikiLinkList.add(cursor.getString(cursor.getColumnIndex("link_info")));  // wikiLink
                otherLinkList.add(cursor.getString(cursor.getColumnIndex("link_survey")));  // otherLink
            } while (cursor.moveToNext());
        }

        // Close cursor and database
        cursor.close();


        // Convert ArrayLists to arrays
        treeNames = treeNameList.toArray(new String[0]);
        treeCodes = treeCodeList.toArray(new String[0]);
        wikiLinks = wikiLinkList.toArray(new String[0]);
        otherLinks = otherLinkList.toArray(new String[0]);

        // Now you can use the arrays (treeNames, treeCodes, wikiLinks, otherLinks)
    }
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this.
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    public void scheduleDailyRainfallCheck() {
        // Calculate the initial delay to 6 AM
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 6);
        calendar.set(Calendar.MINUTE, 00);
        calendar.set(Calendar.SECOND, 0);

        long currentTime = System.currentTimeMillis();
        long targetTime = calendar.getTimeInMillis();

        // If the time is already past 6 AM today, schedule for tomorrow
        if (targetTime <= currentTime) {
            targetTime += TimeUnit.DAYS.toMillis(1);
        }

        long initialDelay = targetTime - currentTime;
//        long initialDelay = 10000;
        // Schedule the worker to repeat daily at 6 AM
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                WaterPlantsWorker.class, 1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "RainfallCheck", ExistingPeriodicWorkPolicy.REPLACE, workRequest);
    }



    private void getLocation() {
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            double latitude = location.getLatitude();
                            double longitude = location.getLongitude();
                            // After getting the location, you can now fetch the weather information
                            getWeatherInformation(latitude, longitude);
                        } else {
                            Toast.makeText(MainActivity.this, "Lấy vị trí thất bại.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLocation();
            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void getWeatherInformation(double latitude, double longitude) {
        // Call the weather API to fetch rainfall and humidity for tomorrow using the latitude and longitude
        fetchWeatherData(latitude, longitude);
    }

    private void fetchWeatherData(double latitude, double longitude) {
//        latitude = 11.102554;
//        longitude = 106.630744;
        String apiKey = "b235f075e09cf9684c46cc7b999844af";  // Replace with your OpenWeatherMap API key
        String url = "https://api.openweathermap.org/data/2.5/forecast?lat=" + latitude + "&lon=" + longitude + "&appid=" + apiKey + "&units=metric&lang=vi";

        // Use a networking library like Retrofit or HttpURLConnection to make the request
        // For simplicity, I'll show an example using HttpURLConnection

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL weatherUrl = new URL(url);
                    HttpURLConnection connection = (HttpURLConnection) weatherUrl.openConnection();
                    connection.setRequestMethod("GET");

                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }

                    String response = stringBuilder.toString();
                    weatherData = parseWeatherData(response);
                    Log.d(TAG, "run: "+weatherData.highestTemperature);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public class WeatherData {
        public double totalRainfall;
        public double highestTemperature;
        public double lowestHumidity;

        public WeatherData(double totalRainfall, double highestTemperature, double lowestHumidity) {
            this.totalRainfall = totalRainfall;
            this.highestTemperature = highestTemperature;
            this.lowestHumidity = lowestHumidity;
        }

        public double getTotalRainfall() {
            return totalRainfall;
        }
    }
    private WeatherData parseWeatherData(String response) {
        double totalRainfall = 0; // Initialize total rainfall
        double highestTemperature = Double.MIN_VALUE; // Initialize highest temperature
        double lowestHumidity = Double.MAX_VALUE; // Initialize lowest humidity

        try {
            JSONObject jsonObject = new JSONObject(response);
            JSONArray listArray = jsonObject.getJSONArray("list");

            // Filter the list to get only the objects for the next 5 days
            for (int i = 0; i < listArray.length(); i++) {
                JSONObject weatherObject = listArray.getJSONObject(i);
                String dtTxt = weatherObject.getString("dt_txt");

                // Check if the date is within the next 5 days
                if (isWithinNextFiveDays(dtTxt)) {
                    JSONObject main = weatherObject.getJSONObject("main");
                    double temp = main.getDouble("temp");
                    int humidity = main.getInt("humidity");

                    // Update highest temperature and lowest humidity
                    if (temp > highestTemperature) {
                        highestTemperature = temp;
                    }
                    if (humidity < lowestHumidity) {
                        lowestHumidity = humidity;
                    }

                    JSONObject rain = weatherObject.optJSONObject("rain");
                    if (rain != null) {
                        totalRainfall += rain.optDouble("3h", 0);
                    }
                }
            }
            SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putFloat("total_rainfall", (float) totalRainfall); // Store as float
            editor.apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return new WeatherData(totalRainfall, highestTemperature, lowestHumidity);
    }

    // Method to check if the date is within the next 5 days
    private boolean isWithinNextFiveDays(String dtTxt) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = sdf.parse(dtTxt);
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, 5);
            return date.before(calendar.getTime());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Method to show the dialog with tree list
    private void showTreeListDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Danh sách cây trồng");

        // Inflate custom layout for dialog content
        View customView = getLayoutInflater().inflate(R.layout.dialog_tree_list, null);
        builder.setView(customView);

        // Set up RecyclerView for the list
        RecyclerView recyclerView = customView.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set custom adapter
        TreeAdapter adapter = new TreeAdapter(treeNames, treeCodes, wikiLinks, otherLinks);
        recyclerView.setAdapter(adapter);

        // Show the dialog
        builder.setPositiveButton("Đóng", null);
        builder.create().show();
    }


    // Custom Adapter for RecyclerView

    // Custom Adapter for RecyclerView
    public class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.TreeViewHolder> {
        private String[] treeNames;
        private String[] treeCodes;
        private String[] wikiLinks;
        private String[] otherLinks;

        public TreeAdapter(String[] treeNames, String[] treeCodes, String[] wikiLinks, String[] otherLinks) {
            this.treeNames = treeNames;
            this.treeCodes = treeCodes;
            this.wikiLinks = wikiLinks;
            this.otherLinks = otherLinks;
        }

        @Override
        public TreeViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_tree, parent, false);
            return new TreeViewHolder(view);
        }

        @Override
        public void onBindViewHolder(TreeViewHolder holder, int position) {
            holder.bind(treeNames[position], treeCodes[position], wikiLinks[position], otherLinks[position]);
        }

        @Override
        public int getItemCount() {
            return treeNames.length;
        }

        // ViewHolder class
        class TreeViewHolder extends RecyclerView.ViewHolder {
            TextView treeNameTextView, wikiLinkTextView, otherLinkTextView;

            TreeViewHolder(View itemView) {
                super(itemView);
                treeNameTextView = itemView.findViewById(R.id.tree_name);
                wikiLinkTextView = itemView.findViewById(R.id.link_wiki);
                otherLinkTextView = itemView.findViewById(R.id.link_other);
            }

            void bind(final String treeName, final String treeCode, final String wikiLink, final String otherLink) {
                treeNameTextView.setText(treeName);
                wikiLinkTextView.setText("Tổng quan");
                otherLinkTextView.setText("Kiểm tra tương thích");

                // Open detailed dialog when tree name is clicked
                treeNameTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showTreeDetailsDialog(treeName, treeCode, wikiLink);
                    }
                });

                // Set onClickListeners for hyperlinks
                wikiLinkTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openLink(wikiLink);
                    }
                });

                otherLinkTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openLink(otherLink);
                    }
                });
            }

            // Method to open a link in browser
            private void openLink(String url) {
                Uri uri = Uri.parse(url);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                itemView.getContext().startActivity(intent);
            }

            // Method to show the second dialog with tree details
            private void showTreeDetailsDialog(String treeName, String treeCode, String wikiLink) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Chi tiết của " + treeName);

                // Inflate the custom dialog layout
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_tree_details, null);
                builder.setView(dialogView);

                // Set tree code
                TextView treeCodeTextView = dialogView.findViewById(R.id.tree_code);
                treeCodeTextView.setText("Mã cây: " + treeCode);

                // Set up date picker button and today checkbox
                Button datePickerButton = dialogView.findViewById(R.id.button_date_picker);
                TextView selectedDateTextView = dialogView.findViewById(R.id.selected_date);
                CheckBox todayCheckBox = dialogView.findViewById(R.id.checkbox_today);

                // Set up plant stages checkboxes




                // DatePicker logic
                datePickerButton.setOnClickListener(v -> {
                    Calendar calendar = Calendar.getInstance();
                    int year = calendar.get(Calendar.YEAR);
                    int month = calendar.get(Calendar.MONTH);
                    int day = calendar.get(Calendar.DAY_OF_MONTH);

                    DatePickerDialog datePickerDialog = new DatePickerDialog(MainActivity.this, (view, year1, month1, dayOfMonth) -> {
                        String selectedDate = dayOfMonth + "/" + (month1 + 1) + "/" + year1;
                        selectedDateTextView.setText("Ngày đã chọn: " + selectedDate);
                        todayCheckBox.setChecked(false);  // Uncheck "Today" if a specific date is chosen
                    }, year, month, day);

                    datePickerDialog.show();
                });

                // Checkbox logic for "Today"
                todayCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedDateTextView.setText("Ngày đã chọn: Hôm nay");
                    } else {
                        selectedDateTextView.setText("Ngày đã chọn: ");
                    }
                });

                // Show the dialog
                builder.setPositiveButton("Lưu", (dialog, which) -> {
                    String selectedDateText = selectedDateTextView.getText().toString();

                    // Create a TreeDetails object to store in the database
                    TreeDetails treeDetails = new TreeDetails();
                    treeDetails.treeName = treeName;
                    treeDetails.treeCode = treeCode;
                    treeDetails.wikiLink = wikiLink;

                    // Define a flexible date formatter


                    try {
                        String rawDateString = selectedDateText.replace("Ngày đã chọn: ", "");  // Remove the prefix

                        // Log the raw date string for debugging
                        Log.d("DEBUG", "Raw date string after replace: " + rawDateString);

                        LocalDate parsedDate = todayCheckBox.isChecked()
                                ? LocalDate.now()  // Get today's date
                                : LocalDate.parse(rawDateString, dateFormatter);  // Parse selected date once

                        // Format the date to the correct pattern
                        String formattedDate = parsedDate.format(dateFormatter);


                        // Assign formatted date to treeDetails
                        treeDetails.selectedDate = formattedDate;

                        // Save data to Room database
                        AppDatabase db = AppDatabase.getDatabase(MainActivity.this);
                        new Thread(() -> {
                            db.treeDetailsDao().insertTreeDetails(treeDetails);
                        }).start();

                        Toast.makeText(MainActivity.this, "Đã lưu dữ liệu cho " + treeName, Toast.LENGTH_SHORT).show();
                        loadTreeData();

                    } catch (Exception e) {
                        // Handle date parsing errors
                        Toast.makeText(MainActivity.this, "Lỗi định dạng ngày: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
                builder.setNegativeButton("Quay lại", null);
                builder.create().show();
            }

        }
    }

    public static class WaterPlantsWorker extends Worker {

        public WaterPlantsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            Log.d("WaterPlantsWorker", "doWork started");

            // Retrieve the total rainfall from SharedPreferences
            SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            double totalRainfall = sharedPreferences.getFloat("total_rainfall", 0);

            // Check if the total rainfall is below 50mm
        if (totalRainfall < 50) {
            sendWaterPlantsNotification();
        }

            // Return success
            return Result.success();
        }

        private void sendWaterPlantsNotification() {
            // Notification logic
            if (ActivityCompat.checkSelfPermission(this.getApplicationContext(), android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "water_plants_channel")
                .setSmallIcon(R.drawable.ic_launcher_background)
                        .setContentTitle("Nhắc nhở tưới nước")
                        .setContentText("Lượng mưa thấp trong 5 ngày tới, cân nhắc tưới nước cho cây trồng của bạn")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    notificationManager.createNotificationChannel(new NotificationChannel("water_plants_channel", "Water Plants Reminder", NotificationManager.IMPORTANCE_DEFAULT));
                }
                notificationManager.notify(1, builder.build());
            }
        }
    }


    @Entity(tableName = "tree_details")
    public static class TreeDetails {
        @PrimaryKey(autoGenerate = true)
        public int id;

        @ColumnInfo(name = "tree_name")
        public String treeName;

        @ColumnInfo(name = "tree_code")
        public String treeCode;

        @ColumnInfo(name = "selected_date")
        public String selectedDate;

        @ColumnInfo(name = "link_info")
        public String wikiLink;
    }

    @Dao
    public interface TreeDetailsDao {

        @Insert
        void insertTreeDetails(TreeDetails treeDetails);

        @Query("SELECT * FROM tree_details WHERE tree_name = :treeName LIMIT 1")
        TreeDetails getTreeDetailsByName(String treeName);

        @Query("SELECT * FROM tree_details")
        List<TreeDetails> getAllTreeDetails();

        @Query("DELETE FROM tree_details WHERE tree_name = :treeName")
        void deleteTreeDetailsByName(String treeName);
        @Query("DELETE FROM tree_details WHERE id = :id")
        void deleteTreeDetailsById(int id);
    }
    @Database(entities = {TreeDetails.class}, version = 2, exportSchema = false)
    public abstract static class AppDatabase extends RoomDatabase {
        public abstract TreeDetailsDao treeDetailsDao();

        private static AppDatabase INSTANCE;

        public static AppDatabase getDatabase(final Context context) {
            if (INSTANCE == null) {
                synchronized (AppDatabase.class) {
                    if (INSTANCE == null) {
                        INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                        AppDatabase.class, "tree_database")
                                .fallbackToDestructiveMigration()
                                .build();
                    }
                }
            }
            return INSTANCE;
        }
    }

    private void loadTreeData() {
        AppDatabase db = AppDatabase.getDatabase(this);

        // Load data in a background thread
        new Thread(() -> {
            List<TreeDetails> treeList = db.treeDetailsDao().getAllTreeDetails();

            // Update UI on the main thread
            runOnUiThread(() -> treeListAdapter.setTreeList(treeList));
        }).start();
    }
// Method to get diseases for the current stage
private List<String> getDiseasesForCurrentStage(String treeCode, int growthDay) {
    List<String> diseases = new ArrayList<>();
    SQLiteDatabase db = dbHelper.getDatabase();
    String query = "SELECT pademic FROM stage WHERE id = ? AND growth_day = ?";
    Cursor cursor = db.rawQuery(query, new String[]{treeCode, String.valueOf(growthDay)});
    if (cursor.moveToFirst()) {
        do {
            diseases.add(cursor.getString(cursor.getColumnIndex("pademic")));
        } while (cursor.moveToNext());
    }
    cursor.close();
    return diseases;
}

// Method to get all diseases for the tree
private List<String> getAllDiseasesForTree(String treeCode) {
    List<String> diseases = new ArrayList<>();
    SQLiteDatabase db = dbHelper.getDatabase();
    String query = "SELECT pademic FROM stage WHERE id = ?";
    Cursor cursor = db.rawQuery(query, new String[]{treeCode});
    if (cursor.moveToFirst()) {
        do {
            diseases.add(cursor.getString(cursor.getColumnIndex("pademic")));
        } while (cursor.moveToNext());
    }
    cursor.close();
    return diseases;
}
    public void showTreeDetailsDialog( TreeDetails tree) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Chi tiết của " + tree.treeName);
        SQLiteDatabase db = dbHelper.getDatabase();
        LocalDate selectedDate = LocalDate.parse(tree.selectedDate, dateFormatter); // Parse using the formatter
        // Query to fetch the data
        String query_tree = "SELECT * FROM tree_read WHERE id = '" + tree.treeCode + "' LIMIT 1";
        Cursor cursor = db.rawQuery(query_tree, null);
        String querry_stage = "SELECT * FROM stage WHERE id = '" + tree.treeCode + "' ORDER BY growth_day DESC";
        Cursor cursor_stage = db.rawQuery(querry_stage, null);

        View dialogView = getLayoutInflater().inflate(R.layout.activity_plant_details, null);
        builder.setView(dialogView);
        if (cursor_stage.moveToFirst()) {
            do {
                // Count days till today
                long diff = ChronoUnit.DAYS.between(selectedDate, LocalDate.now()); // Calculate the difference in days
                int growth_day = Integer.parseInt(cursor_stage.getString(cursor_stage.getColumnIndex("growth_day")));
                if (diff < growth_day) {
                    TextView plantStatusTextView = dialogView.findViewById(R.id.tv_plant_status);
                    plantStatusTextView.setText(cursor_stage.getString(cursor_stage.getColumnIndex("stage")));  // Set the desired status value here
                    // Get diseases for the current stage
                    List<String> diseases = getDiseasesForCurrentStage(tree.treeCode, growth_day);
                    TextView commonDiseasesTextView = dialogView.findViewById(R.id.tv_common_diseases);
                    if (!diseases.isEmpty()) {
                        commonDiseasesTextView.setText(TextUtils.join(", ", diseases));
                    } else {
                        // If no diseases for the current stage, get all diseases for the tree
                        diseases = getAllDiseasesForTree(tree.treeCode);
                        commonDiseasesTextView.setText(TextUtils.join(", ", diseases));
                    }
                }
            } while (cursor_stage.moveToNext());
        }
        TextView treeCodeTextView = dialogView.findViewById(R.id.tv_name);
        treeCodeTextView.setText(tree.treeName);
        TextView selectedDateTextView = dialogView.findViewById(R.id.tv_start_date);
        selectedDateTextView.setText(tree.selectedDate);
            // Update the temperature in tv_weather_conditions
        TextView weatherConditionsTextView = dialogView.findViewById(R.id.tv_weather_conditions);
            weatherConditionsTextView.setText("Nhiệt độ cao nhất: " + weatherData.highestTemperature + "°C");

            // Show alert if humidity is below 29%
            if (weatherData.lowestHumidity < 29) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Cảnh báo cháy rừng")
                        .setMessage("Độ ẩm thấp dưới 29%, nguy cơ cháy rừng cao!")
                        .setPositiveButton("OK", null)
                        .show();
            }

        Button btnCareRecommendation = dialogView.findViewById(R.id.btn_care_recommendation);
        btnCareRecommendation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = tree.wikiLink; // Replace with your actual URL ; // Replace with your actual URL
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                startActivity(intent);
            }
        });
        Button btnMoreInfo = dialogView.findViewById(R.id.btn_disease_treatment);
        btnMoreInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = tree.wikiLink; // Replace with your actual URL
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                startActivity(intent);
            }
        });
        builder.setPositiveButton("Xóa", (dialog, which) -> {
            deleteTreeData(tree.id);
            Toast.makeText(MainActivity.this, "Đã xóa dữ liệu cho " + tree.treeName, Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Quay lại", null);
        builder.create().show();
    }

    private void deleteTreeData(int treeId) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getDatabase(MainActivity.this);
            db.treeDetailsDao().deleteTreeDetailsById(treeId);
            runOnUiThread(this::loadTreeData);
        }).start();
    }

    public static class TreeListAdapter extends RecyclerView.Adapter<TreeListAdapter.TreeViewHolder> {

        private List<TreeDetails> treeList;
        private Context context;

        // Constructor to pass the data
        public TreeListAdapter(Context context, List<TreeDetails> treeList) {
            this.context = context;
            this.treeList = treeList;
        }

        @NonNull
        @Override
        public TreeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.tree_item, parent, false);
            return new TreeViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TreeViewHolder holder, int position) {
            TreeDetails tree = treeList.get(position);
            holder.treeNameTextView.setText(tree.treeName);

            // Handle "More" button click to show tree details
            holder.moreButton.setOnClickListener(v -> {
                if (context instanceof MainActivity) {
                    ((MainActivity) context).showTreeDetailsDialog(tree);
                }
            });
        }

        @Override
        public int getItemCount() {
            return treeList != null ? treeList.size() : 0;
        }

        // ViewHolder class
        static class TreeViewHolder extends RecyclerView.ViewHolder {
            TextView treeNameTextView, treeCodeTextView;
            Button moreButton;

            public TreeViewHolder(@NonNull View itemView) {
                super(itemView);
                treeNameTextView = itemView.findViewById(R.id.tree_name);
                treeCodeTextView = itemView.findViewById(R.id.tree_code);
                moreButton = itemView.findViewById(R.id.button_more);
            }
        }

        // Method to update the data in the adapter
        public void setTreeList(List<TreeDetails> newTreeList) {
            this.treeList = newTreeList;
            notifyDataSetChanged();
        }
    }


    public class DatabaseHelper extends SQLiteOpenHelper {

        private static final String DATABASE_NAME = "treedata.db";
        private static final String DATABASE_PATH = "/data/data/com.example.treeapp/databases/";
        private static final int DATABASE_VERSION = 1;
        private Context context;
        private SQLiteDatabase database;

        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            this.context = context;
        }

        // Check if the database exists
        private boolean checkDatabase() {
            File dbFile = new File(DATABASE_PATH + DATABASE_NAME);
            return dbFile.exists();
        }

        // Copy the database from assets to the internal storage
        private void copyDatabase() throws IOException {
            InputStream input = context.getAssets().open(DATABASE_NAME);
            String outFileName = DATABASE_PATH + DATABASE_NAME;
            OutputStream output = new FileOutputStream(outFileName);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }

            output.flush();
            output.close();
            input.close();
        }

        public void openDatabase() throws SQLException {
            String dbPath = DATABASE_PATH + DATABASE_NAME;
            database = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // No need to create tables if you're using a pre-existing database
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Handle database upgrade logic
        }

        public SQLiteDatabase getDatabase() {
            return this.database;
        }

        public void initializeDatabase() {
//            if (!checkDatabase()) {
                try {
                    this.getReadableDatabase(); // This will create the database folder
                    copyDatabase();
                } catch (IOException e) {
                    throw new Error("Error copying database: " + e.getMessage());
//                }
            }
        }
    }


    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
