import React, {useEffect, useState} from 'react';
import Login from './components/Login';
import './App.css';
import Cookies from 'js-cookie';

function MainContent({ isLoggedIn }) {
    return (
        <div className="main-content">
            <div className="login-container">
                <Login isLoggedIn={isLoggedIn}/>
            </div>
        </div>
    );
}

function App() {
    const [isLoggedIn, setIsLoggedIn] = useState(false);

    useEffect(() => {
        // Check for the 'jwt' cookie on mount
        setIsLoggedIn(!(Cookies.get('jwt-payload') == null));

        // Re-check on focus to reflect login changes after popup flow
        const onFocus = () => setIsLoggedIn(!!Cookies.get('jwt-payload'));
        window.addEventListener('focus', onFocus);
        return () => window.removeEventListener('focus', onFocus);
    }, []);

    return (
        <div className="App">
            {<MainContent isLoggedIn={isLoggedIn}/>}
        </div>);
}

export default App;
