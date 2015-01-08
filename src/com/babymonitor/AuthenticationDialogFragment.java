package com.babymonitor;

import ip.cam.babymonitor.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.EditText;

public class AuthenticationDialogFragment extends DialogFragment {

	/* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface AuthenticationDialogListener {
        public void onAuthenticationDialogPositiveClick(String username, String password, boolean remember);
        public void onAuthenticationDialogNegativeClick();
    }
    
    // Use this instance of the interface to deliver action events
    AuthenticationDialogListener mListener;
    
    // Override the Fragment.onAttach() method to instantiate the AuthenticationDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the AuthenticationDialogListener so we can send events to the host
            mListener = (AuthenticationDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement AuthenticationDialogListener");
        }
    }
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(inflater.inflate(R.layout.dialog_auth, null))
        // Add action buttons
               .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int id) {
                       EditText username = (EditText) ((Dialog)dialog).findViewById(R.id.username);
                       EditText password = (EditText) ((Dialog)dialog).findViewById(R.id.password);
                       CheckBox remember = (CheckBox) ((Dialog)dialog).findViewById(R.id.remember);
                       
                       // Send the positive button event back to the host activity
                       mListener.onAuthenticationDialogPositiveClick(username.getText().toString(), password.getText().toString(), remember.isChecked());
                   }
               })
               .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                	   AuthenticationDialogFragment.this.getDialog().cancel();
                	   
                	   // Send the negative button event back to the host activity
                       mListener.onAuthenticationDialogNegativeClick();
                   }
               });      
        return builder.create();
    }
}
