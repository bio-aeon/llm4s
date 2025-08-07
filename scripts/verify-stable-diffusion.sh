#!/bin/bash

# Stable Diffusion WebUI Verification Script
# This script verifies that Stable Diffusion WebUI is properly set up and running

echo "🔍 Verifying Stable Diffusion WebUI Setup..."
echo "================================================"

# Check if the API is responding
echo "1. Checking API availability..."
if curl -s --max-time 5 http://localhost:7860/sdapi/v1/options > /dev/null; then
    echo "✅ API is responding at http://localhost:7860"
else
    echo "❌ API is not responding. Is Stable Diffusion WebUI running?"
    echo "   Start it with: cd ~/workspace/home/sd/stable-diffusion-webui && ./webui.sh"
    exit 1
fi

# Check GPU/CPU mode (if on macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo ""
    echo "2. Checking GPU acceleration on macOS..."
    
    if [ -d "$HOME/workspace/home/sd/stable-diffusion-webui/venv" ]; then
        cd ~/workspace/home/sd/stable-diffusion-webui
        source venv/bin/activate
        
        MPS_AVAILABLE=$(python -c "import torch; print(torch.backends.mps.is_available())" 2>/dev/null)
        
        if [ "$MPS_AVAILABLE" = "True" ]; then
            echo "✅ MPS (Metal Performance Shaders) GPU acceleration is available"
        else
            echo "⚠️  MPS not available - running in CPU mode (slower)"
        fi
        
        deactivate
    else
        echo "⚠️  Virtual environment not found"
    fi
fi

# Test image generation
echo ""
echo "3. Testing image generation..."
echo "   This will generate a small test image..."

# Simple test request
TEST_REQUEST='{
  "prompt": "a simple red circle on white background",
  "steps": 5,
  "width": 128,
  "height": 128,
  "batch_size": 1
}'

RESPONSE=$(curl -s --max-time 30 -X POST \
  -H "Content-Type: application/json" \
  -d "$TEST_REQUEST" \
  http://localhost:7860/sdapi/v1/txt2img)

if echo "$RESPONSE" | grep -q '"images"'; then
    echo "✅ Image generation successful!"
    
    # Check if response contains base64 image data
    if echo "$RESPONSE" | grep -q '"images":\['; then
        echo "✅ Received image data in response"
    fi
else
    echo "❌ Image generation failed"
    echo "Response: $RESPONSE"
    exit 1
fi

echo ""
echo "4. Performance check..."
# Get generation info from the response
if echo "$RESPONSE" | grep -q '"info"'; then
    INFO=$(echo "$RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('info', 'No info available'))")
    echo "Generation details: $INFO"
fi

echo ""
echo "🎉 Stable Diffusion WebUI is properly configured and working!"
echo ""
echo "Next steps:"
echo "1. Run the LLM4S image generation sample:"
echo "   cd ~/workspace/home/llm4s"
echo "   sbt \"runMain org.llm4s.imagegeneration.examples.ImageGenerationExample\""
echo ""
echo "2. Or access the web interface at: http://localhost:7860"
echo ""
echo "For better performance on Apple Silicon, ensure you're using GPU mode:"
echo "   Check webui-user.sh contains: --api --listen --port 7860 --skip-torch-cuda-test --no-half"
echo "   (without --use-cpu all)"