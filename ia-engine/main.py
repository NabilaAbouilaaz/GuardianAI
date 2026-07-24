from fastapi import FastAPI

app = FastAPI(title="GuardianAI - IA Engine")

@app.get("/health")
def health():
    return {"status": "ok"}