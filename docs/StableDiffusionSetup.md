# Stable Diffusion Setup Guide for LLM4S Image Generation

This guide explains how to set up and run the Stable Diffusion image generation sample in LLM4S.

## Prerequisites

### 1. Stable Diffusion WebUI Installation

The LLM4S image generation sample requires AUTOMATIC1111's Stable Diffusion WebUI to be running locally or on a remote server. The WebUI provides a REST API that LLM4S connects to for generating images.

#### System Requirements
- **GPU**: 
  - NVIDIA GPU with 4GB+ VRAM (Windows/Linux)
  - Apple Silicon M1/M2/M3/M4 (macOS)
  - CPU mode available but very slow
- **OS**: Windows 10/11, Linux, or macOS
- **Python**: Version 3.10.6 (critical - not 3.11 or later)
- **Git**: For cloning repositories
- **Storage**: At least 15GB free space

## Complete Setup Guide for macOS (Apple Silicon)

### Step 1: Install Python 3.10.6

If you have pyenv installed (recommended):
```bash
# Install Python 3.10.6
pyenv install 3.10.6

# Set it as local version for the project
cd ~/workspace/home/sd/stable-diffusion-webui
pyenv local 3.10.6
```

Alternative: Download from [python.org](https://www.python.org/downloads/release/python-3106/)

### Step 2: Clone and Configure

```bash
# Create working directory
mkdir -p ~/workspace/home/sd
cd ~/workspace/home/sd

# Clone the repository
git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui.git
cd stable-diffusion-webui

# Set Python 3.10.6 for this directory (if using pyenv)
pyenv local 3.10.6
```

### Step 3: Configure for GPU (Apple Silicon) or CPU Mode

Edit `webui-user.sh` to configure the startup options:

#### For GPU Mode (Apple Silicon M1/M2/M3/M4) - RECOMMENDED
```bash
# Fast generation using Metal Performance Shaders (MPS)
export COMMANDLINE_ARGS="--api --listen --port 7860 --skip-torch-cuda-test --no-half"
```

#### For CPU Mode (slow but works everywhere)
```bash
# Slow but compatible with any system
export COMMANDLINE_ARGS="--api --listen --port 7860 --skip-torch-cuda-test --no-half --use-cpu all"
```

### Step 4: Run the WebUI

```bash
./webui.sh
```

First run will:
1. Create a Python virtual environment
2. Install PyTorch and dependencies
3. Download the default Stable Diffusion v1.5 model (3.97GB)
4. Start the web interface at http://localhost:7860

## Installation for Other Platforms

### Windows
1. Install Python 3.10.6 (ensure "Add Python to PATH" is checked)
2. Install Git
3. Clone the repository:
   ```bash
   git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui.git
   ```
4. Edit `webui-user.bat` for GPU mode:
   ```batch
   set COMMANDLINE_ARGS=--api --xformers
   ```
5. Run:
   ```bash
   webui-user.bat
   ```

### Linux (NVIDIA GPU)
```bash
# Clone repository
git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui.git
cd stable-diffusion-webui

# Edit webui-user.sh for GPU mode
echo 'export COMMANDLINE_ARGS="--api --xformers"' >> webui-user.sh

# Run
./webui.sh
```

## Verifying Your Setup

### Check GPU/CPU Mode (macOS)

```bash
# Activate the virtual environment
cd ~/workspace/home/sd/stable-diffusion-webui
source venv/bin/activate

# Check if using GPU (MPS) or CPU
python -c "import torch; print(f'MPS (GPU) available: {torch.backends.mps.is_available()}')"
```

### API Verification

The Stable Diffusion WebUI API is enabled with the `--api` flag. Verify it's working:

```bash
# Quick check if API is responding
curl http://localhost:7860/sdapi/v1/options | head -c 100

# Should return JSON configuration data
```

### Automated Verification Script

Use the provided verification script to test your complete setup:

```bash
cd ~/workspace/home/llm4s
./scripts/verify-stable-diffusion.sh
```

This script will:
- Check if the API is responding
- Verify GPU acceleration status (macOS)
- Test image generation with a simple request
- Provide performance information

### Performance Expectations

| Platform | Mode | Typical Speed (512x512) |
|----------|------|------------------------|
| Apple M1/M2 | MPS (GPU) | 5-15 seconds |
| Apple M3/M4 | MPS (GPU) | 3-10 seconds |
| Apple Silicon | CPU | 2-5 minutes |
| NVIDIA RTX 3080 | CUDA | 2-5 seconds |
| NVIDIA RTX 4090 | CUDA | 1-3 seconds |

### Memory Management

If you encounter memory issues on Apple Silicon, add these flags to `webui-user.sh`:

```bash
# Medium memory optimization
export COMMANDLINE_ARGS="--api --listen --port 7860 --skip-torch-cuda-test --no-half --medvram"

# Maximum memory optimization (slower)
export COMMANDLINE_ARGS="--api --listen --port 7860 --skip-torch-cuda-test --no-half --lowvram"
```

## Running the LLM4S Sample

### Basic Example

Once Stable Diffusion WebUI is running, you can run the LLM4S image generation sample:

```bash
sbt "runMain org.llm4s.imagegeneration.examples.ImageGenerationExample"
```

This sample demonstrates:
- Basic image generation with default settings
- Advanced options (custom size, seed, negative prompts)
- Multiple image generation
- Error handling

### Sample Code Overview

The sample will attempt to:

1. **Basic Generation**: Create a "sunset over mountains" image
2. **Advanced Generation**: Create a cyberpunk city with specific settings:
   - Size: 768x512 (landscape)
   - Seed: 42 (for reproducible results)
   - Guidance scale: 8.0
   - Negative prompt: "blurry, low quality"
3. **Multiple Images**: Generate 3 robot images
4. **Error Handling**: Demonstrate handling of connection failures

### Expected Output

When successful, you'll see output like:
```
=== Image Generation API Demo ===

--- Basic Example ---
✓ Generated image: 512x512
✓ Saved to: sunset.png

--- Advanced Example ---
✓ Generated cyberpunk image with seed: 42
✓ Saved: cyberpunk_42.png

--- Multiple Images Example ---
✓ Generated 3 robot images
✓ Saved: robot_1.png
✓ Saved: robot_2.png
✓ Saved: robot_3.png
```

### Configuration Options

The `StableDiffusionConfig` supports:
- `baseUrl`: URL of the WebUI (default: `http://localhost:7860`)
- `apiKey`: Optional API key for secured instances
- `timeout`: Request timeout in milliseconds (default: 120000)

The `ImageGenerationOptions` supports:
- `size`: Image dimensions (e.g., `ImageSize.Square512`, `ImageSize.Landscape768x512`)
- `format`: Output format (`ImageFormat.PNG` or `ImageFormat.JPEG`)
- `seed`: Specific seed for reproducible results
- `guidanceScale`: How closely to follow the prompt (default: 7.0)
- `inferenceSteps`: Number of diffusion steps (default: 20)
- `negativePrompt`: What to avoid in the image
- `samplerName`: Sampling method (default: "Euler a")

## Troubleshooting

### Connection Refused Error

If you see an error like:
```
✓ Expected connection error: Connection refused
```

This means the Stable Diffusion WebUI is not running. Ensure:
1. The WebUI is started (`webui-user.bat` or `./webui.sh`)
2. It's accessible at `http://localhost:7860`
3. No firewall is blocking port 7860

### Out of Memory Errors

If the WebUI crashes with OOM errors:
1. Add `--medvram` or `--lowvram` to the launch arguments
2. Reduce the image size in `ImageGenerationOptions`
3. Reduce the `inferenceSteps` parameter

### Slow Generation

To improve performance:
1. Add `--xformers` to the WebUI launch arguments (if supported)
2. Use a smaller image size
3. Reduce the number of inference steps

## Alternative: Using Other Providers

LLM4S also supports other image generation providers:

### OpenAI DALL-E
```scala
val options = ImageGenerationOptions(size = ImageSize.Square1024)
ImageGeneration.generateWithDallE("A futuristic city", options)
```

### Hugging Face
```scala
val hfClient = ImageGeneration.huggingFaceClient(
  apiKey = sys.env("HF_API_TOKEN"),
  model = "runwayml/stable-diffusion-v1-5"
)
hfClient.generateImage("A cute robot")
```

## Additional Resources

- [Stable Diffusion WebUI Documentation](https://github.com/AUTOMATIC1111/stable-diffusion-webui/wiki)
- [LLM4S Image Generation API Documentation](../src/main/scala/org/llm4s/imagegeneration/README.md)
- [API Reference](ImageGeneration.md)