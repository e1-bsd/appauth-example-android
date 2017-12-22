package com.ef.appauthexample

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatButton
import android.support.v7.widget.AppCompatTextView
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.ef.appauthexample.MainApplication.Companion.LOG_TAG
import kotlinx.android.synthetic.main.activity_main.*
import net.openid.appauth.*
import org.json.JSONException

private val AUTH_CLIENT_ID = "efpv2.mobile.client"
private val AUTH_RESPONSE_ACTION = "com.ef.appauthexample.HANDLE_AUTHORIZATION_RESPONSE"
private val AUTH_REDIRECT_URL = Uri.parse("com.ef.appauthexample:/oauth2callback")

private val AUTH_SERVICE_CONFIG = AuthorizationServiceConfiguration(
        Uri.parse("https://passport-qa.ef.com/connect/authorize"),
        Uri.parse("https://passport-qa.ef.com/connect/token")
)

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private val SHARED_PREFERENCES_NAME = "AuthStatePreference"
    private val AUTH_STATE = "AUTH_STATE"
    private val USED_INTENT = "USED_INTENT"

    private var mMainApplication: MainApplication? = null
    val RC_AUTH = 1000
    // state
    var mAuthState: AuthState? = null

    // views
    private var mAuthorize: AppCompatButton? = null
    private var mMakeApiCall: AppCompatButton? = null
    private var mSignOut: AppCompatButton? = null
    private var mGivenName: AppCompatTextView? = null
    private var mFamilyName: AppCompatTextView? = null
    private var mFullName: AppCompatTextView? = null
    private var mProfileView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mMainApplication = application as MainApplication
        mAuthorize = findViewById<AppCompatButton>(R.id.authorize)
        mMakeApiCall = findViewById<AppCompatButton>(R.id.makeApiCall)
        mSignOut = findViewById<AppCompatButton>(R.id.signOut)
        mGivenName = findViewById<AppCompatTextView>(R.id.givenName)
        mFamilyName = findViewById<AppCompatTextView>(R.id.familyName)
        mFullName = findViewById<AppCompatTextView>(R.id.fullName)
        mProfileView = findViewById<ImageView>(R.id.profileImage)
        tv_access_token.movementMethod = ScrollingMovementMethod.getInstance()
        enablePostAuthorizationFlows()

        mAuthorize?.setOnClickListener(this)
    }

    private fun enablePostAuthorizationFlows() {
        mAuthState = restoreAuthState()
        val authState = mAuthState

        if (authState != null && authState.isAuthorized) {
            if (mMakeApiCall!!.visibility == View.GONE) {
                mMakeApiCall!!.visibility = View.VISIBLE
                mMakeApiCall!!.setOnClickListener(MakeApiCallListener(this, authState, AuthorizationService(this)))
            }
            if (mSignOut!!.visibility == View.GONE) {
                mSignOut!!.visibility = View.VISIBLE
                mSignOut!!.setOnClickListener(SignOutListener(this))
            }
        } else {
            mMakeApiCall!!.visibility = View.GONE
            mSignOut!!.visibility = View.GONE
        }
    }

    /**
     * Exchanges the code, for the [TokenResponse].
     *
     * @param intent represents the [Intent] from the Custom Tabs or the System Browser.
     */
    private fun handleAuthorizationResponse(intent: Intent) {
        val response = AuthorizationResponse.fromIntent(intent)
        val error = AuthorizationException.fromIntent(intent)
        val authState = AuthState(response, error)

        if (response != null) {
            Log.i(LOG_TAG, String.format("Handled Authorization Response %s ", authState.jsonSerializeString()))
            val service = AuthorizationService(this)
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
                if (exception != null) {
                    Log.w(LOG_TAG, "Token Exchange failed", exception)
                } else {
                    if (tokenResponse != null) {
                        authState.update(tokenResponse, exception)
                        authState.performActionWithFreshTokens(service, AuthState.AuthStateAction { accessToken, idToken, ex ->
                            if (ex != null) {

                            }
                            tv_access_token.text = accessToken


                        })
                        persistAuthState(authState)
                        Log.i(LOG_TAG, String.format("Token Response [ Access Token: %s, ID Token: %s ]", tokenResponse.accessToken, tokenResponse.idToken))
                    }
                }
            }
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun persistAuthState(authState: AuthState) {
        getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
                .putString(AUTH_STATE, authState.jsonSerializeString())
                .commit()
        enablePostAuthorizationFlows()
    }

    private fun clearAuthState() {
        getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(AUTH_STATE)
                .apply()
    }

    private fun restoreAuthState(): AuthState? {
        val jsonString = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(AUTH_STATE, null)
        if (!TextUtils.isEmpty(jsonString)) {
            try {
                return AuthState.jsonDeserialize(jsonString)
            } catch (jsonException: JSONException) {
                // should never happen
            }

        }
        return null
    }

    override fun onNewIntent(intent: Intent) {
        checkIntent(intent)
    }

    private fun checkIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        when (action) {
            AUTH_RESPONSE_ACTION -> if (!intent.hasExtra(USED_INTENT)) {
                handleAuthorizationResponse(intent)
                intent.putExtra(USED_INTENT, true)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        checkIntent(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == RC_AUTH) {
            handleAuthorizationResponse(data)
        } else {
            // ...
        }
    }

    override fun onClick(view: View?) {
        val request = AuthorizationRequest.Builder(
                AUTH_SERVICE_CONFIG,
                AUTH_CLIENT_ID,
                ResponseTypeValues.CODE,
                AUTH_REDIRECT_URL
        )
                .setScopes("openid", "efpv2")
                .build()
        val authService = AuthorizationService(this)
        val authIntent = authService.getAuthorizationRequestIntent(request)
        startActivityForResult(authIntent, RC_AUTH)
    }


    class SignOutListener(private val mMainActivity: MainActivity) : View.OnClickListener {
        override fun onClick(view: View) {
            mMainActivity.mAuthState = null
            mMainActivity.clearAuthState()
            mMainActivity.enablePostAuthorizationFlows()
        }
    }

    class MakeApiCallListener(private val mMainActivity: MainActivity, private val mAuthState: AuthState, private val mAuthorizationService: AuthorizationService) : View.OnClickListener {
        override fun onClick(view: View) {

            // code from the section 'Making API Calls' goes here

        }
    }

}
