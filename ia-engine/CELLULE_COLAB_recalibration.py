# =====================================================================
# GuardianAI — Recalibration honnête du seuil
#
# À copier dans une nouvelle cellule du notebook GuardianAI_LightGBM_EMBER2024,
# sur Colab.
#
# CE QUE ÇA CORRIGE
# -----------------
# La version actuelle choisit le seuil sur le jeu de test, puis annonce les
# performances sur ce même jeu :
#
#     fpr, tpr, thr = roc_curve(yte, p)                 # jeu de test
#     seuil = thr[np.where(fpr <= 0.02)[0][-1]]
#     cm = confusion_matrix(yte, (p >= seuil))          # même jeu de test
#
# Le taux de faux positifs vaut alors 2,00 % par construction, et le taux de
# détection est légèrement optimiste. Ici, le jeu de test est coupé en deux :
# le seuil est calibré sur une moitié, les performances mesurées sur l'autre,
# restée vierge.
#
# On ne réentraîne pas : le modèle sauvegardé est rechargé tel quel.
# =====================================================================

import json
import os

import joblib
import numpy as np
from sklearn.metrics import confusion_matrix, roc_auc_score, roc_curve
from sklearn.model_selection import train_test_split

DATA = "/content/ember2024"
OUT = "/content/drive/MyDrive/GuardianAI/models"
DIM = 2568

assert os.path.exists(f"{DATA}/X_test.dat"), (
    "Vecteurs absents : la VM a ete recyclee. Relancer d'abord le telechargement "
    "et create_vectorized_features, mais PAS l'entrainement."
)


def load(nom):
    X = np.memmap(f"{DATA}/X_{nom}.dat", dtype=np.float32, mode="r").reshape(-1, DIM)
    y = np.asarray(np.memmap(f"{DATA}/y_{nom}.dat", dtype=np.int32, mode="r"))
    return X, y


model = joblib.load(f"{OUT}/lightgbm_ember2024_v2.joblib")
print("Modele recharge — aucun reentrainement.")

# --- 1. Prédictions sur l'ensemble du jeu de test, par lots pour la mémoire
X_test, y_test = load("test")
it = np.where((y_test == 0) | (y_test == 1))[0]
y = y_test[it].astype(np.int8)

p = np.empty(len(it))
for i in range(0, len(it), 20000):
    p[i:i + 20000] = model.predict(np.asarray(X_test[it[i:i + 20000]]))
del X_test, y_test

print(f"Jeu de test complet : {len(y)} fichiers  {np.bincount(y)}")

# --- 2. Séparation en validation (calibration) et test (mesure)
#     Stratifiée, pour conserver l'équilibre des classes dans les deux moitiés.
i_val, i_test = train_test_split(
    np.arange(len(y)), test_size=0.5, random_state=42, stratify=y
)
print(f"validation : {len(i_val)}   |   test final : {len(i_test)}")

# --- 3. Le seuil est choisi UNIQUEMENT sur la validation
fpr, tpr, thr = roc_curve(y[i_val], p[i_val])
seuil = float(thr[np.where(fpr <= 0.02)[0][-1]])
print(f"\nSeuil calibre sur la validation : {seuil:.4f}")

# --- 4. Les performances sont mesurées UNIQUEMENT sur le test resté vierge
cm = confusion_matrix(y[i_test], (p[i_test] >= seuil).astype(int))
fp_rate = cm[0, 1] / cm[0].sum()
det = cm[1, 1] / cm[1].sum()
auc = float(roc_auc_score(y[i_test], p[i_test]))

print(f"\n=== Resultats honnetes (jeu de test non utilise pour le seuil) ===")
print(cm)
print(f"ROC-AUC        : {auc:.4f}")
print(f"Faux positifs  : {fp_rate * 100:.2f}%   [plafond vise : 2,00%]")
print(f"Detection      : {det * 100:.2f}%")

# --- 5. Comparaison avec l'ancienne méthode, sur le jeu de test entier
fpr_o, _, thr_o = roc_curve(y, p)
seuil_o = float(thr_o[np.where(fpr_o <= 0.02)[0][-1]])
cm_o = confusion_matrix(y, (p >= seuil_o).astype(int))
print(f"\n--- Ancienne methode (seuil calibre sur le test lui-meme) ---")
print(f"Seuil {seuil_o:.4f} | FP {cm_o[0,1]/cm_o[0].sum()*100:.2f}% | "
      f"Detection {cm_o[1,1]/cm_o[1].sum()*100:.2f}%")
print("L'ecart entre les deux mesure l'optimisme de l'ancienne methode.")

# --- 6. Jeu challenge : on VERIFIE les etiquettes au lieu de les supposer
X_chal, y_chal = load("challenge")
print(f"\n=== Jeu challenge ===")
print("Etiquettes presentes :", np.unique(y_chal, return_counts=True))

mal = np.where(y_chal == 1)[0]
if len(mal) == 0:
    print("ATTENTION : aucune etiquette 1. Le taux ne peut pas etre calcule ainsi.")
else:
    pc = np.empty(len(mal))
    for i in range(0, len(mal), 20000):
        pc[i:i + 20000] = model.predict(np.asarray(X_chal[mal[i:i + 20000]]))
    det_chal = float((pc >= seuil).mean())
    print(f"Fichiers malveillants confirmes : {len(mal)} / {len(y_chal)}")
    print(f"Detection sur les evasifs       : {det_chal * 100:.2f}%")

# --- 7. Métriques corrigées, dans un fichier distinct de l'ancien
metriques = {
    "seuil": seuil,
    "roc_auc": auc,
    "faux_positifs": float(fp_rate),
    "detection": float(det),
    "detection_challenge": det_chal if len(mal) else None,
    "dataset": "EMBER2024 (.NET + Win64)",
    "n_train": 150000,
    "methode_seuil": "calibre sur une moitie du jeu de test, mesure sur l'autre",
    "n_validation": int(len(i_val)),
    "n_test": int(len(i_test)),
}
json.dump(metriques, open(f"{OUT}/metrics_ember2024_v3.json", "w"), indent=2)
print(f"\nEcrit : {OUT}/metrics_ember2024_v3.json")
print(json.dumps(metriques, indent=2))
