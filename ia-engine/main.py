from fastapi import FastAPI, UploadFile, File, HTTPException
import joblib
import numpy as np
import ember
import os

app = FastAPI(title="GuardianAI - IA Engine")

MODEL_PATH = os.path.join(os.path.dirname(__file__), "models", "xgboost_baseline_v1.joblib")
model = joblib.load(MODEL_PATH)
extractor = ember.PEFeatureExtractor()

MODEL_VERSION = "xgboost_baseline_v1"

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    contents = await file.read()

    try:
        features = extractor.feature_vector(contents)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Impossible d'extraire les features : {e}")

    X = np.array(features).reshape(1, -1)
    proba_malicious = float(model.predict_proba(X)[0][1])

    if proba_malicious < 0.3:
        classification = "benin"
    elif proba_malicious < 0.7:
        classification = "suspect"
    else:
        classification = "malveillant"

    return {
        "filename": file.filename,
        "classification": classification,
        "confidence_score": round(proba_malicious * 100, 2),
        "model_version": MODEL_VERSION
    }   