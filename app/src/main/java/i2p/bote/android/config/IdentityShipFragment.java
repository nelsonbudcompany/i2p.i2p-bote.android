package i2p.bote.android.config;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;

import i2p.bote.I2PBote;
import i2p.bote.android.R;
import i2p.bote.android.util.RobustAsyncTask;
import i2p.bote.android.util.TaskFragment;

public abstract class IdentityShipFragment extends Fragment {
    private Callbacks mCallbacks = sDummyCallbacks;

    public interface Callbacks {
        public void onTaskFinished();
    }

    private static Callbacks sDummyCallbacks = new Callbacks() {
        public void onTaskFinished() {}
    };

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof Callbacks))
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = sDummyCallbacks;
    }

    // Code to identify the fragment that is calling onActivityResult().
    static final int SHIP_WAITER = 0;
    // Tag so we can find the task fragment again, in another
    // instance of this fragment after rotation.
    static final String SHIP_WAITER_TAG = "shipWaiterTask";

    TextView mError;

    public static IdentityShipFragment newInstance(boolean exporting) {
        return new ExportIdentitiesFragment(); // TODO implement importing
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        ShipWaiterFrag f = (ShipWaiterFrag) getFragmentManager().findFragmentByTag(SHIP_WAITER_TAG);
        if (f != null)
            f.setTargetFragment(this, SHIP_WAITER);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mError = (TextView) view.findViewById(R.id.error);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SHIP_WAITER) {
            if (resultCode == Activity.RESULT_OK) {
                mCallbacks.onTaskFinished();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                setInterfaceEnabled(true);
                mError.setText(data.getStringExtra("error"));
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    protected abstract void setInterfaceEnabled(boolean enabled);

    public static class ShipWaiterFrag extends TaskFragment<Object, String, String> {
        static final String SHIP_FILE = "shipFile";
        static final String PASSWORD = "password";

        String currentStatus;
        TextView mStatus;

        public static ShipWaiterFrag newInstance(File shipFile, String password) {
            ShipWaiterFrag f = new ShipWaiterFrag();
            Bundle args = new Bundle();
            args.putSerializable(SHIP_FILE, shipFile);
            args.putString(PASSWORD, password);
            f.setArguments(args);
            return f;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.dialog_status, container, false);
            mStatus = (TextView) v.findViewById(R.id.status);

            if (currentStatus != null && !currentStatus.isEmpty())
                mStatus.setText(currentStatus);

            return v;
        }

        @Override
        public Object[] getParams() {
            Bundle args = getArguments();
            return new Object[]{
                    (File) args.getSerializable(SHIP_FILE),
                    args.getString(PASSWORD),
            };
        }

        @Override
        public void updateProgress(String... values) {
            currentStatus = values[0];
            mStatus.setText(currentStatus);
        }

        @Override
        public void taskFinished(String result) {
            super.taskFinished(result);

            if (getTargetFragment() != null) {
                Intent i = new Intent();
                i.putExtra("result", result);
                getTargetFragment().onActivityResult(
                        getTargetRequestCode(), Activity.RESULT_OK, i);
            }
        }

        @Override
        public void taskCancelled(String error) {
            super.taskCancelled(error);

            if (getTargetFragment() != null) {
                Intent i = new Intent();
                i.putExtra("error", error);
                getTargetFragment().onActivityResult(
                        getTargetRequestCode(), Activity.RESULT_CANCELED, i);
            }
        }
    }

    public static class ExportIdentitiesFragment extends IdentityShipFragment {
        EditText mExportFilename;
        TextView mSuffix;
        CheckBox mEncrypt;
        View mPasswordEntry;
        EditText mPassword;
        EditText mConfirmPassword;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_export_identities, container, false);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            mExportFilename = (EditText) view.findViewById(R.id.export_filename);
            mSuffix = (TextView) view.findViewById(R.id.suffix);
            mEncrypt = (CheckBox) view.findViewById(R.id.encrypt);
            mPasswordEntry = view.findViewById(R.id.password_entry);
            mPassword = (EditText) view.findViewById(R.id.password);
            mConfirmPassword = (EditText) view.findViewById(R.id.password_confirm);

            mEncrypt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    mSuffix.setText(b ? ".bote" : ".txt");
                    mPasswordEntry.setVisibility(b ? View.VISIBLE : View.GONE);
                }
            });

            view.findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String exportFilename = mExportFilename.getText().toString();
                    String suffix = mSuffix.getText().toString();
                    boolean encrypt = mEncrypt.isChecked();
                    String password = null;

                    if (encrypt) {
                        password = mPassword.getText().toString();
                        String confirmPassword = mConfirmPassword.getText().toString();
                        if (password.isEmpty()) {
                            mError.setText(R.string.password_empty);
                            return;
                        } else if (!password.equals(confirmPassword)) {
                            mError.setText(R.string.passwords_do_not_match);
                            return;
                        }
                    }

                    File exportFile = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOCUMENTS
                    ), exportFilename + suffix);
                    if (exportFile.exists()) {
                        // TODO ask to rename or overwrite
                        mError.setText(R.string.file_exists);
                        return;
                    } else
                        exportIdentities(exportFile, password);
                }
            });
        }

        private void exportIdentities(File exportFile, String password) {
            setInterfaceEnabled(false);
            mError.setText("");

            ShipWaiterFrag f = ShipWaiterFrag.newInstance(exportFile, password);
            f.setTask(new ExportWaiter());
            f.setTargetFragment(ExportIdentitiesFragment.this, SHIP_WAITER);
            getFragmentManager().beginTransaction()
                    .replace(R.id.waiter_frag, f, SHIP_WAITER_TAG)
                    .commit();
        }

        @Override
        protected void setInterfaceEnabled(boolean enabled) {
            mExportFilename.setEnabled(enabled);
            mEncrypt.setEnabled(enabled);
            mPassword.setEnabled(enabled);
            mConfirmPassword.setEnabled(enabled);
        }

        private class ExportWaiter extends RobustAsyncTask<Object, String, String> {
            @Override
            protected String doInBackground(Object... params) {
                try {
                    publishProgress("Exporting identities");
                    I2PBote.getInstance().getIdentities().export(
                            (File) params[0],
                            (String) params[1]);
                    return null;
                } catch (Throwable e) {
                    cancel(false);
                    return e.getMessage();
                }
            }
        }
    }
}