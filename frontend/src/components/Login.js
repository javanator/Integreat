import React from "react";
import '../config';
import {API_OAUTH_QB_LOGIN, API_OAUTH_QB_LOGOUT, API_OAUTH_QB_REFRESH} from "../config";

function Login({isLoggedIn}) {
    const loginWithQuickBooks = () => {
        // Start the OAuth flow; adjust path if your backend uses a different route
        // Option 1: Same window
        // window.location.href = '/connectToQuickbooks';
        // Option 2: Popup window (uncomment to use)
        const w = 800, h = 650;
        const left = (window.screen.width - w) / 2;
        const top = (window.screen.height - h) / 2;
        window.open(API_OAUTH_QB_LOGIN, 'connectPopup', `location=1,width=${w},height=${h},left=${left},top=${top}`);
    };

    const logoutWithQuickbooks = () => {
        const w = 800, h = 650;
        const left = (window.screen.width - w) / 2;
        const top = (window.screen.height - h) / 2;
        window.open(API_OAUTH_QB_LOGOUT, 'connectPopup', `location=1,width=${w},height=${h},left=${left},top=${top}`);
    }

    const refreshCall = () => {
        const w = 800, h = 650;
        const left = (window.screen.width - w) / 2;
        const top = (window.screen.height - h) / 2;
        window.open(API_OAUTH_QB_REFRESH, 'connectPopup', `location=1,width=${w},height=${h},left=${left},top=${top}`);
        //window.location.href = API_OAUTH_QB_REFRESH;
    }

    let authButton;
    if (isLoggedIn === true) {
        authButton = (
            <button className="login-btn" onClick={logoutWithQuickbooks}>
                Disconnect From Quickbooks
            </button>
        );
    } else {
        authButton = (
            <button className="login-btn" onClick={loginWithQuickBooks} aria-label="Login with QuickBooks">
                Connect with QuickBooks
            </button>
        );
    }

    let refreshButton = (
        <button className="login-btn" onClick={refreshCall}>
            Refresh
        </button>
    );

    return (
        <div className="login-container">
            {authButton} {refreshButton}
        </div>
    );
}

export default Login;