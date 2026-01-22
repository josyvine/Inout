package com.inout.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.inout.app.databinding.FragmentAdminEmployeesBinding;
import com.inout.app.models.User;
import com.inout.app.adapters.EmployeeListAdapter; // Assumes adapter is in a sub-package

import java.util.ArrayList;
import java.util.List;

public class AdminEmployeesFragment extends Fragment implements EmployeeListAdapter.OnEmployeeActionListener {

    private static final String TAG = "AdminEmployeesFrag";
    private FragmentAdminEmployeesBinding binding;
    private FirebaseFirestore db;
    private EmployeeListAdapter adapter;
    private List<User> employeeList;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminEmployeesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        employeeList = new ArrayList<>();
        
        setupRecyclerView();
        listenForEmployees();
    }

    private void setupRecyclerView() {
        binding.recyclerViewEmployees.setLayoutManager(new LinearLayoutManager(getContext()));
        // Initialize adapter with empty list and this fragment as the listener
        adapter = new EmployeeListAdapter(getContext(), employeeList, this);
        binding.recyclerViewEmployees.setAdapter(adapter);
    }

    private void listenForEmployees() {
        binding.progressBar.setVisibility(View.VISIBLE);

        // Query: Get all users where role is "employee"
        db.collection("users")
                .whereEqualTo("role", "employee")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        binding.progressBar.setVisibility(View.GONE);

                        if (error != null) {
                            Log.e(TAG, "Listen failed.", error);
                            return;
                        }

                        if (value != null) {
                            employeeList.clear();
                            for (DocumentSnapshot doc : value) {
                                User user = doc.toObject(User.class);
                                if (user != null) {
                                    // Ensure UID is set from the document ID if missing in object
                                    user.setUid(doc.getId());
                                    employeeList.add(user);
                                }
                            }
                            // Notify adapter
                            adapter.notifyDataSetChanged();
                            
                            if (employeeList.isEmpty()) {
                                binding.tvEmptyView.setVisibility(View.VISIBLE);
                            } else {
                                binding.tvEmptyView.setVisibility(View.GONE);
                            }
                        }
                    }
                });
    }

    // Callback from Adapter when "Approve" button is clicked
    @Override
    public void onApproveClicked(User user) {
        showApproveDialog(user);
    }

    // Callback from Adapter when "Delete" (optional) is clicked
    @Override
    public void onDeleteClicked(User user) {
        showDeleteDialog(user);
    }

    private void showApproveDialog(User user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Approve Employee");
        builder.setMessage("Enter a unique Employee ID (e.g., EMP001) to approve " + user.getName());

        // Set up the input
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint("EMP001");
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("Approve", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String empId = input.getText().toString().trim();
                if (!empId.isEmpty()) {
                    approveUserInFirestore(user, empId);
                } else {
                    Toast.makeText(getContext(), "Employee ID is required!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void approveUserInFirestore(User user, String empId) {
        // Update Firestore: Approved = true, EmployeeID = input
        db.collection("users").document(user.getUid())
                .update("approved", true, "employeeId", empId)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(getContext(), "Employee Approved Successfully.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getContext(), "Error approving employee: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showDeleteDialog(User user) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove Employee")
                .setMessage("Are you sure you want to remove " + user.getName() + "? This cannot be undone.")
                .setPositiveButton("Remove", (dialog, which) -> {
                    db.collection("users").document(user.getUid())
                            .delete()
                            .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "User removed.", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(getContext(), "Error removing user.", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}