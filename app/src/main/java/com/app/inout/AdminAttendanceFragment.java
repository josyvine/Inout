package com.inout.app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.inout.app.adapters.AttendanceAdapter;
import com.inout.app.databinding.FragmentAdminAttendanceBinding;
import com.inout.app.models.AttendanceRecord;
import com.inout.app.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin view for Attendance.
 * 1. Select employee from Spinner.
 * 2. View monthly records in a CSV-style horizontal table.
 */
public class AdminAttendanceFragment extends Fragment {

    private static final String TAG = "AdminAttendanceFrag";
    private FragmentAdminAttendanceBinding binding;
    private FirebaseFirestore db;
    
    private List<User> employees;
    private List<AttendanceRecord> attendanceLogs;
    private AttendanceAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminAttendanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        employees = new ArrayList<>();
        attendanceLogs = new ArrayList<>();

        setupRecyclerView();
        loadEmployeeList();
    }

    private void setupRecyclerView() {
        binding.rvAttendanceTable.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AttendanceAdapter(attendanceLogs);
        binding.rvAttendanceTable.setAdapter(adapter);
    }

    /**
     * Fetches all approved employees to populate the selection spinner.
     */
    private void loadEmployeeList() {
        binding.progressBar.setVisibility(View.VISIBLE);
        db.collection("users")
                .whereEqualTo("role", "employee")
                .whereEqualTo("approved", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    binding.progressBar.setVisibility(View.GONE);
                    employees.clear();
                    List<String> employeeNames = new ArrayList<>();
                    employeeNames.add("Select an Employee");

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            employees.add(user);
                            employeeNames.add(user.getName() + " (" + user.getEmployeeId() + ")");
                        }
                    }

                    setupSpinner(employeeNames);
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Error loading employees", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupSpinner(List<String> names) {
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), 
                android.R.layout.simple_spinner_item, names);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerEmployees.setAdapter(spinnerAdapter);

        binding.spinnerEmployees.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    // Position 0 is the hint
                    User selectedUser = employees.get(position - 1);
                    loadAttendanceForEmployee(selectedUser);
                } else {
                    attendanceLogs.clear();
                    adapter.notifyDataSetChanged();
                    binding.tableHeader.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * Loads attendance logs for a specific employee from Firestore.
     */
    private void loadAttendanceForEmployee(User user) {
        binding.progressBar.setVisibility(View.VISIBLE);
        
        // Query logs for the selected employee, ordered by timestamp descending
        db.collection("attendance")
                .whereEqualTo("employeeId", user.getEmployeeId())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    binding.progressBar.setVisibility(View.GONE);
                    
                    if (error != null) {
                        Log.e(TAG, "Error listening for logs", error);
                        Toast.makeText(getContext(), "Error loading logs", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (value != null) {
                        attendanceLogs.clear();
                        for (DocumentSnapshot doc : value) {
                            AttendanceRecord record = doc.toObject(AttendanceRecord.class);
                            if (record != null) {
                                attendanceLogs.add(record);
                            }
                        }
                        
                        adapter.notifyDataSetChanged();
                        
                        if (attendanceLogs.isEmpty()) {
                            binding.tvNoData.setVisibility(View.VISIBLE);
                            binding.tableHeader.setVisibility(View.GONE);
                        } else {
                            binding.tvNoData.setVisibility(View.GONE);
                            binding.tableHeader.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}