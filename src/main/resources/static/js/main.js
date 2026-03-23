document.addEventListener('DOMContentLoaded', () => {
    const advancedToggle = document.getElementById('advanced-toggle');
    const advancedOptions = document.getElementById('advanced-options');
    const shortenForm = document.getElementById('shorten-form');
    const analyticsForm = document.getElementById('analytics-form');
    const resultBox = document.getElementById('result-box');
    const shortUrlDisplay = document.getElementById('shortUrlDisplay');
    const copyBtn = document.getElementById('copy-btn');
    const submitBtn = document.getElementById('submit-btn');

    // Create Toast element
    const toast = document.createElement('div');
    toast.className = 'toast';
    document.body.appendChild(toast);

    function showToast(message, type = 'success') {
        toast.textContent = message;
        toast.className = `toast ${type} show`;
        setTimeout(() => {
            toast.classList.remove('show');
        }, 3000);
    }

    // Toggle advanced options
    advancedToggle.addEventListener('click', () => {
        const isHidden = advancedOptions.style.display === 'none';
        advancedOptions.style.display = isHidden ? 'grid' : 'none';
        advancedToggle.innerHTML = isHidden ? 
            '<span>- Hide Advanced Options</span>' : 
            '<span>+ Advanced Options (Alias, Expiration)</span>';
    });

    // Handle Url Shorten Form
    shortenForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        submitBtn.textContent = 'Snipping...';
        submitBtn.disabled = true;

        const originalUrl = document.getElementById('originalUrl').value;
        const customAlias = document.getElementById('customAlias').value;
        const expiry = document.getElementById('expiry').value;

        const payload = { originalUrl };
        if (customAlias) payload.customAlias = customAlias;
        if (expiry) payload.expiry = expiry + ':00'; // Make sure the datetime format is compatible with Spring's expected LocalDateTime parsing

        try {
            const response = await fetch('/api/shorten', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            const data = await response.text(); 
            let parsedData = {};
            try { parsedData = JSON.parse(data); } catch(err) { parsedData = { message: data }; }
            
            if (response.ok) {
                shortUrlDisplay.textContent = parsedData.shortUrl;
                shortUrlDisplay.href = parsedData.shortUrl;
                resultBox.style.display = 'block';
                showToast('URL Snipped successfully!');
                
                // Clear inputs
                document.getElementById('originalUrl').value = '';
                document.getElementById('customAlias').value = '';
                document.getElementById('expiry').value = '';
            } else {
                let errorMsg = parsedData.message || 'Failed to shorten URL';
                if (parsedData.originalUrl) {
                    errorMsg = parsedData.originalUrl;
                }
                showToast(errorMsg, 'error');
            }
        } catch (error) {
            showToast('Network error occurred.', 'error');
        } finally {
            submitBtn.textContent = 'Shorten';
            submitBtn.disabled = false;
        }
    });

    // Copy to clipboard
    copyBtn.addEventListener('click', (e) => {
        e.preventDefault();
        navigator.clipboard.writeText(shortUrlDisplay.textContent).then(() => {
            const originalText = copyBtn.textContent;
            copyBtn.textContent = 'Copied!';
            showToast('Copied to clipboard!');
            setTimeout(() => {
                copyBtn.textContent = originalText;
            }, 2000);
        });
    });

    // Handle Analytics
    analyticsForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        let rawCode = document.getElementById('analyticsCode').value.trim();
        if (rawCode.endsWith('/')) rawCode = rawCode.slice(0, -1);
        const code = rawCode.split('/').pop(); // Extracts just 'c' from 'http://localhost:8080/c'
        const btn = analyticsForm.querySelector('button');
        
        btn.textContent = 'Loading...';
        btn.disabled = true;

        try {
            const response = await fetch(`/api/analytics/${code}`);
            
            if (response.ok) {
                const data = await response.json();
                document.getElementById('analytics-result').style.display = 'block';
                document.getElementById('stat-clicks').textContent = data.clickCount;
                
                const dateString = new Date(data.createdAt).toLocaleDateString(undefined, {
                    year: 'numeric', month: 'short', day: 'numeric'
                });
                document.getElementById('stat-created').textContent = dateString;
                
                showToast('Analytics retrieved!');
            } else {
                showToast('Short code not found.', 'error');
                document.getElementById('analytics-result').style.display = 'none';
            }
        } catch (error) {
            showToast('Network error occurred.', 'error');
        } finally {
            btn.textContent = 'Track';
            btn.disabled = false;
        }
    });
});
