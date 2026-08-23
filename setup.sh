#!/bin/bash
set -e
echo "=== COINGLASS INTELLIGENCE KURULUM ==="
if ! command -v python3 &> /dev/null; then
    echo "python3 bulunamadi. Python 3.11+ gerekli."; exit 1
fi
PYVER=$(python3 -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')")
echo "-> Python $PYVER"
echo "-> venv olusturuluyor..."
python3 -m venv .venv
source .venv/bin/activate
echo "-> Bagimliliklar kuruluyor..."
pip install --upgrade pip
pip install -r requirements.txt
echo "-> Chromium indiriliyor..."
python3 -m playwright install chromium
python3 -m playwright install-deps chromium 2>/dev/null || true
mkdir -p data/db data/browser_profile data/endpoint_cache data/logs
echo ""
echo "=== Kurulum tamamlandi! ==="
echo "  source .venv/bin/activate"
echo "  python main.py BTCUSDT"
echo "  python main.py ETH --pages open_interest funding liquidation"
echo "  python main.py SOL --discover-only"
echo "  python main.py BTC --headless"
