package cn.ottohub.oh2013;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import cn.ottohub.oh2013.api.OttoAuthApi;

/**
 * OTTOhub 邮箱/UID 密码登录。
 */
public class OttoLoginFragment extends Fragment {

    private EditText inputUidEmail;
    private EditText inputPassword;
    private Button btnLogin;
    private ProgressBar loginProgress;
    private Handler handler = new Handler();
    private boolean fromSetup;

    public static OttoLoginFragment newInstance(boolean fromSetup) {
        Bundle args = new Bundle();
        args.putBoolean("from_setup", fromSetup);
        OttoLoginFragment fragment = new OttoLoginFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            fromSetup = getArguments().getBoolean("from_setup", false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_otto_login, container, false);
        inputUidEmail = (EditText) view.findViewById(R.id.input_uid_email);
        inputPassword = (EditText) view.findViewById(R.id.input_password);
        btnLogin = (Button) view.findViewById(R.id.btn_login);
        loginProgress = (ProgressBar) view.findViewById(R.id.login_progress);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });
        return view;
    }

    private void attemptLogin() {
        final String uidEmail = inputUidEmail.getText().toString().trim();
        final String password = inputPassword.getText().toString();
        if (uidEmail.length() == 0 || password.length() == 0) {
            Toast.makeText(getActivity(), "请输入账号和密码", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int code = OttoAuthApi.login(uidEmail, password);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        setLoading(false);
                        if (getActivity() == null) return;
                        if (code == OttoAuthApi.LOGIN_OK) {
                            Toast.makeText(getActivity(), getString(R.string.specialloginactivity_toast_767b), Toast.LENGTH_SHORT).show();
                            getActivity().setResult(LoginActivity.RESULT_OK);
                            if (fromSetup) {
                                startActivity(new Intent(getActivity(), MainActivity.class));
                            }
                            getActivity().finish();
                        } else if (code == OttoAuthApi.LOGIN_NETWORK) {
                            Toast.makeText(getActivity(), "网络错误，请重试", Toast.LENGTH_SHORT).show();
                        } else {
                            String msg = OttoAuthApi.getLastErrorMessage();
                            if (msg == null || msg.length() == 0) {
                                msg = "登录失败，请检查账号或密码";
                            }
                            Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void setLoading(boolean loading) {
        if (loginProgress != null) loginProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnLogin != null) btnLogin.setEnabled(!loading);
        if (inputUidEmail != null) inputUidEmail.setEnabled(!loading);
        if (inputPassword != null) inputPassword.setEnabled(!loading);
    }
}
