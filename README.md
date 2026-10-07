# GarminAIApp

### Overview
This project contains an Android Application and Garmin ConnectIQ application which work together to allow a Garmin Venu 4 device to communicate with a locally run LLM (ollama).
Because the Garmin Venu 4 wifi capability has limitations, any outside requests must route through phone Bluetooth. The Android application serves a dual purpose, first as a way to chat with the LLM (becuase thats cool too) but primarily as a request passthrough for Garmin

### Technologies
* Ollama: local AI engine
* Llama 3: local AI LLM
* Tailscale: used to bridge phone to home PC running model
* Android: use Kotlin and multiple different technologies to create phone application
* Garmin Connect IQ App (TODO): will create a simple Garmin Connect IQ which can communicate with phone to reach model.

### Run Guide
1. Downlaod and install Ollama, download an AI model (llama3), configure Ollama to allow localhost access, and note down port (likely 11434)
2. Set up a Tailscale private Network, adding both PC and phone and testing connectivity
3. Configure Android project secrets: create secrets.properties in root and set OLLAMA_URL="http://YOUR_TAILSCALE_IP:11434/api/generate"
4. Build and install application onto phone

TODO: Garmin app