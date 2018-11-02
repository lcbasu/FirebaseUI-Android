package com.firebase.ui.auth.ui.email;

import android.app.AlertDialog;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import com.firebase.ui.auth.ErrorCodes;
import com.firebase.ui.auth.FirebaseAuthAnonymousUpgradeException;
import com.firebase.ui.auth.FirebaseUiException;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.R;
import com.firebase.ui.auth.data.model.FlowParameters;
import com.firebase.ui.auth.data.model.UserCancellationException;
import com.firebase.ui.auth.ui.InvisibleActivityBase;
import com.firebase.ui.auth.util.ExtraConstants;
import com.firebase.ui.auth.viewmodel.RequestCodes;
import com.firebase.ui.auth.viewmodel.ResourceObserver;
import com.firebase.ui.auth.viewmodel.email.EmailLinkSignInHandler;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EmailLinkCatcherActivity extends InvisibleActivityBase {

    private EmailLinkSignInHandler mHandler;

    public static Intent createIntent(Context context, FlowParameters flowParams) {
        return createBaseIntent(context, EmailLinkCatcherActivity.class, flowParams);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initHandler();

        if (getFlowParams().emailLink != null) {
            mHandler.startSignIn();
        }
    }

    private void initHandler() {
        mHandler = ViewModelProviders.of(this).get(EmailLinkSignInHandler.class);
        mHandler.init(getFlowParams());
        mHandler.getOperation().observe(this, new ResourceObserver<IdpResponse>(this) {
            @Override
            protected void onSuccess(@NonNull IdpResponse response) {
                finish(RESULT_OK, response.toIntent());
            }

            @Override
            protected void onFailure(@NonNull final Exception e) {
                if (e instanceof UserCancellationException) {
                    finish(RESULT_CANCELED, null);
                } else if (e instanceof FirebaseAuthAnonymousUpgradeException) {
                    IdpResponse res = ((FirebaseAuthAnonymousUpgradeException) e).getResponse();
                    finish(RESULT_CANCELED, new Intent().putExtra(ExtraConstants
                            .IDP_RESPONSE, res));
                } else if (e instanceof FirebaseUiException) {
                    int errorCode = ((FirebaseUiException) e).getErrorCode();
                    if (errorCode == ErrorCodes.EMAIL_LINK_WRONG_DEVICE_ERROR
                            || errorCode == ErrorCodes.INVALID_EMAIL_LINK_ERROR) {
                        buildAlertDialog(errorCode).show();
                    } else if (errorCode == ErrorCodes.EMAIL_LINK_PROMPT_FOR_EMAIL_ERROR
                            || errorCode == ErrorCodes.EMAIL_MISMATCH_ERROR) {
                        recoverFromEmailMismatchError();
                    }
                } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    recoverFromEmailMismatchError();
                } else {
                    finish(RESULT_CANCELED, IdpResponse.getErrorIntent(e));
                }
            }
        });
    }

    private void recoverFromEmailMismatchError() {
        Intent intent = EmailLinkErrorRecoveryActivity.createIntent(getApplicationContext(),
                getFlowParams());
        startActivityForResult(intent, RequestCodes.EMAIL_LINK_PROMPT_FOR_EMAIL_FLOW);
    }

    private AlertDialog buildAlertDialog(int errorCode) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        if (errorCode == ErrorCodes.EMAIL_LINK_WRONG_DEVICE_ERROR) {
            alertDialog.setTitle(R.string.fui_email_link_wrong_device_header)
                    .setMessage(R.string.fui_email_link_wrong_device_message)
                    .setPositiveButton(R.string.fui_email_link_dismiss_button,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    finish(RequestCodes.EMAIL_LINK_WRONG_DEVICE_FLOW, null);
                                }
                            });
        } else if (errorCode == ErrorCodes.INVALID_EMAIL_LINK_ERROR) {
            alertDialog.setTitle(R.string.fui_email_link_invalid_link_header)
                    .setMessage(R.string.fui_email_link_invalid_link_message)
                    .setPositiveButton(R.string.fui_email_link_dismiss_button,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    finish(RequestCodes.EMAIL_LINK_INVALID_LINK_FLOW, null);
                                }
                            });
        }
        return alertDialog.create();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == RequestCodes.EMAIL_LINK_PROMPT_FOR_EMAIL_FLOW) {
            IdpResponse response = IdpResponse.fromResultIntent(data);
            // CheckActionCode is called before starting this flow, so we only get here
            // if the sign in link is valid - it can only fail by being cancelled.
            if (resultCode == RESULT_OK) {
                finish(RESULT_OK, response.toIntent());
            } else {
                finish(RESULT_CANCELED, null);
            }
        }
    }
}
