package com.ef.appauthexample

import android.app.Dialog
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.provider.AuthCallback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials


class MainActivity : AppCompatActivity() {

    private var token: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        token = findViewById<View>(R.id.token) as TextView
        val loginButton = findViewById<Button>(R.id.loginButton)
        loginButton.setOnClickListener({ login() })
    }

    private fun login() {
        token!!.setText("Not logged in")
        val auth0 = Auth0(this)
        auth0.isOIDCConformant = true
        WebAuthProvider.init(auth0)
                .withScheme("demo")
                .withAudience(String.format("https://%s/userinfo", getString(R.string.com_auth0_domain)))
                .start(this@MainActivity, object : AuthCallback {
                    override fun onFailure(dialog: Dialog) {
                        runOnUiThread { dialog.show() }
                    }

                    override fun onFailure(exception: AuthenticationException) {
                        runOnUiThread { Toast.makeText(this@MainActivity, "Error: " + exception.message, Toast.LENGTH_SHORT).show() }
                    }

                    override fun onSuccess(credentials: Credentials) {
                        runOnUiThread { token!!.setText("Logged in: " + credentials.getAccessToken()) }
                    }
                })
    }

}
