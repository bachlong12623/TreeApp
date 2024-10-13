package com.example.treeapp;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    private String[] treeNames = {"Sầu riêng", "Cà phê", "Trà"};
    private String[] treeCodes = {"T001", "T002", "T003"};
    private String[] wikiLinks = {
            "https://en.wikipedia.org/wiki/Oak",
            "https://en.wikipedia.org/wiki/Pine",
            "https://en.wikipedia.org/wiki/Maple"
    };
    private String[] otherLinks = {
            "https://example.com/oak",
            "https://example.com/pine",
            "https://example.com/maple"
    };
    private RecyclerView recyclerView;
    private TreeListAdapter treeListAdapter;


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
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
        // Set up RecyclerView
        recyclerView = findViewById(R.id.recycler_view_trees);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        treeListAdapter = new TreeListAdapter(this, null); // Pass context and null data initially
        recyclerView.setAdapter(treeListAdapter);

        // Load data from Room database
        loadTreeData();
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
        calendar.set(Calendar.HOUR_OF_DAY, 20);
        calendar.set(Calendar.MINUTE, 38);
        calendar.set(Calendar.SECOND, 0);

        long currentTime = System.currentTimeMillis();
        long targetTime = calendar.getTimeInMillis();

        // If the time is already past 6 AM today, schedule for tomorrow
        if (targetTime <= currentTime) {
            targetTime += TimeUnit.DAYS.toMillis(1);
        }

//        long initialDelay = targetTime - currentTime;
        long initialDelay = 10000;
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
                            Toast.makeText(MainActivity.this, "Location: " + latitude + ", " + longitude, Toast.LENGTH_SHORT).show();

                            // After getting the location, you can now fetch the weather information
                            getWeatherInformation(latitude, longitude);
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to get location.", Toast.LENGTH_SHORT).show();
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
                    parseWeatherData(response);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void parseWeatherData(String response) {
        double totalRainfall = 0; // Initialize total rainfall

        try {
            JSONObject jsonObject = new JSONObject(response);
            JSONArray listArray = jsonObject.getJSONArray("list");

            // Filter the list to get only the objects for the next 5 days
            for (int i = 0; i < listArray.length(); i++) {
                JSONObject weatherObject = listArray.getJSONObject(i);
                String dtTxt = weatherObject.getString("dt_txt");

                // Check if the date is within the next 5 days
                if (isWithinNextFiveDays(dtTxt)) {
                    JSONObject rain = weatherObject.optJSONObject("rain");
                    if (rain != null) {
                        totalRainfall += rain.optDouble("3h", 0);
                    }
                }
            }

            Log.d("TotalRainfall", String.valueOf(totalRainfall));

            // Store the total rainfall in SharedPreferences
            SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putFloat("total_rainfall", (float) totalRainfall); // Store as float
            editor.apply();

        } catch (JSONException e) {
            e.printStackTrace();
        }
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
        builder.setTitle("Tree List");

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
                        showTreeDetailsDialog(treeName, treeCode);
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
            private void showTreeDetailsDialog(String treeName, String treeCode) {
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
                CheckBox seedlingCheckBox = dialogView.findViewById(R.id.checkbox_seedling);
                CheckBox saplingCheckBox = dialogView.findViewById(R.id.checkbox_sapling);
                CheckBox matureCheckBox = dialogView.findViewById(R.id.checkbox_mature);

                // Logic to make only one checkbox selectable at a time
                seedlingCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        saplingCheckBox.setChecked(false);
                        matureCheckBox.setChecked(false);
                    }
                });

                saplingCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        seedlingCheckBox.setChecked(false);
                        matureCheckBox.setChecked(false);
                    }
                });

                matureCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        seedlingCheckBox.setChecked(false);
                        saplingCheckBox.setChecked(false);
                    }
                });

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
                    String selectedDate = selectedDateTextView.getText().toString();
                    String plantStage = seedlingCheckBox.isChecked() ? "Seedling"
                            : saplingCheckBox.isChecked() ? "Sapling"
                            : matureCheckBox.isChecked() ? "Mature" : "";

                    // Create a TreeDetails object to store in the database
                    TreeDetails treeDetails = new TreeDetails();
                    treeDetails.treeName = treeName;
                    treeDetails.treeCode = treeCode;
                    treeDetails.selectedDate = String.valueOf(todayCheckBox.isChecked() ? LocalDate.now() : LocalDate.parse(selectedDateTextView.getText().toString().replace("Ngày đã chọn: ", ""), DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                    treeDetails.plantStage = plantStage;

                    // Save data to Room database
                    AppDatabase db = AppDatabase.getDatabase(MainActivity.this);
                    new Thread(() -> {
                        db.treeDetailsDao().insertTreeDetails(treeDetails);
                    }).start();

                    Toast.makeText(MainActivity.this, "Đã lưu dữ liệu cho " + treeName, Toast.LENGTH_SHORT).show();
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

        @ColumnInfo(name = "plant_stage")
        public String plantStage;
    }

    @Dao
    public interface TreeDetailsDao {

        @Insert
        void insertTreeDetails(TreeDetails treeDetails);

        @Query("SELECT * FROM tree_details WHERE tree_name = :treeName LIMIT 1")
        TreeDetails getTreeDetailsByName(String treeName);

        @Query("SELECT * FROM tree_details")
        List<TreeDetails> getAllTreeDetails();
    }
    @Database(entities = {TreeDetails.class}, version = 1, exportSchema = false)
    public abstract static class AppDatabase extends RoomDatabase {
        public abstract TreeDetailsDao treeDetailsDao();

        private static AppDatabase INSTANCE;

        public static AppDatabase getDatabase(final Context context) {
            if (INSTANCE == null) {
                synchronized (AppDatabase.class) {
                    if (INSTANCE == null) {
                        INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                        AppDatabase.class, "tree_database")
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

    // This function is now accessible from the TreeListAdapter to show the dialog
    public void showTreeDetailsDialog(String treeName, String treeCode) {
        // Your existing code for showing tree details dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Chi tiết của " + treeName);

        // Inflate the custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tree_details, null);
        builder.setView(dialogView);

        TextView treeCodeTextView = dialogView.findViewById(R.id.tree_code);
        treeCodeTextView.setText("Mã cây: " + treeCode);

        // Other logic and views for showing the details...

        builder.setPositiveButton("Lưu", (dialog, which) -> {
            // Save logic or other actions after selecting data
            Toast.makeText(MainActivity.this, "Đã lưu dữ liệu cho " + treeName, Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Quay lại", null);
        builder.create().show();
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
            holder.treeCodeTextView.setText(tree.treeCode);

            // Handle "More" button click to show tree details
            holder.moreButton.setOnClickListener(v -> {
                if (context instanceof MainActivity) {
                    ((MainActivity) context).showTreeDetailsDialog(tree.treeName, tree.treeCode);
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


    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
