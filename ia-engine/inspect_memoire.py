"""
GuardianAI — Limite de taille reellement analysable.

Le cahier des charges annonce 200 Mo par fichier (UC-01). Une extraction a
pourtant echoue sur un binaire de 4 Mo avec :

    ArrayMemoryError: Unable to allocate 30.4 MiB for an array
    with shape (3988632,) and data type int64

L'origine est dans thrember :

    counts = np.bincount(np.frombuffer(bytez, dtype=np.uint8), minlength=256)

np.bincount promeut chaque octet en entier 64 bits. Un fichier de N octets
demande donc 8N octets pour cette seule operation, auxquels s'ajoutent le
fichier lui-meme et les tampons intermediaires.

Ce script mesure la consommation reelle sur des fichiers de tailles croissantes
et en deduit la limite tenable sur cette machine.

Usage (venv active) :
    python inspect_memoire.py
    python inspect_memoire.py --max-mo 150
"""

import argparse
import gc
import glob
import os
import tracemalloc

import numpy as np
import thrember

extracteur = thrember.PEFeatureExtractor()


def memoire_disponible_mo():
    """Memoire physique libre, sans dependance externe."""
    try:
        import ctypes

        class MemoryStatusEx(ctypes.Structure):
            _fields_ = [
                ("dwLength", ctypes.c_ulong),
                ("dwMemoryLoad", ctypes.c_ulong),
                ("ullTotalPhys", ctypes.c_ulonglong),
                ("ullAvailPhys", ctypes.c_ulonglong),
                ("ullTotalPageFile", ctypes.c_ulonglong),
                ("ullAvailPageFile", ctypes.c_ulonglong),
                ("ullTotalVirtual", ctypes.c_ulonglong),
                ("ullAvailVirtual", ctypes.c_ulonglong),
                ("ullAvailExtendedVirtual", ctypes.c_ulonglong),
            ]

        stat = MemoryStatusEx()
        stat.dwLength = ctypes.sizeof(MemoryStatusEx)
        ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(stat))
        return stat.ullTotalPhys / 1e6, stat.ullAvailPhys / 1e6
    except Exception:
        return None, None


def candidats(max_octets):
    """Executables du systeme, un par palier de taille."""
    fichiers = []
    for motif in (r"C:\Windows\System32\*.exe",
                  r"C:\Windows\System32\*.dll",
                  r"C:\Program Files\**\*.exe",
                  r"C:\Program Files\**\*.dll"):
        try:
            fichiers += glob.glob(motif, recursive=True)
        except Exception:
            pass

    tailles = {}
    for f in fichiers:
        try:
            t = os.path.getsize(f)
        except OSError:
            continue
        if t > max_octets or t < 100_000:
            continue
        # Un representant par palier : 0-1 Mo, 1-2 Mo, 2-4 Mo, 4-8 Mo...
        palier = int(np.log2(max(t, 1) / 1e6) * 2) if t > 1e6 else 0
        tailles.setdefault(palier, f)

    return sorted(tailles.values(), key=os.path.getsize)


def tester(chemin):
    """Analyse un fichier et retourne (succes, pic memoire en Mo, message)."""
    taille = os.path.getsize(chemin)
    with open(chemin, "rb") as f:
        contenu = f.read()

    gc.collect()
    tracemalloc.start()
    try:
        extracteur.feature_vector(contenu)
        _, pic = tracemalloc.get_traced_memory()
        return True, pic / 1e6, ""
    except MemoryError as e:
        _, pic = tracemalloc.get_traced_memory()
        return False, pic / 1e6, str(e)[:90]
    except Exception as e:
        _, pic = tracemalloc.get_traced_memory()
        return False, pic / 1e6, f"{type(e).__name__}: {str(e)[:70]}"
    finally:
        tracemalloc.stop()
        del contenu
        gc.collect()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--max-mo", type=float, default=60,
                        help="Taille maximale testee, en Mo.")
    args = parser.parse_args()

    total, dispo = memoire_disponible_mo()
    if total:
        print(f"Memoire physique : {total/1000:.1f} Go au total, "
              f"{dispo/1000:.1f} Go disponibles\n")

    fichiers = candidats(args.max_mo * 1e6)
    if not fichiers:
        print("Aucun fichier de test trouve.")
        return

    print(f"{'Fichier':<34} {'Taille':>9} {'Pic memoire':>13} {'Ratio':>7}  Resultat")
    print("-" * 86)

    ratios = []
    for chemin in fichiers:
        taille_mo = os.path.getsize(chemin) / 1e6
        ok, pic_mo, msg = tester(chemin)
        ratio = pic_mo / taille_mo if taille_mo else 0
        if ok:
            ratios.append(ratio)
        nom = os.path.basename(chemin)[:33]
        etat = "OK" if ok else f"ECHEC — {msg}"
        print(f"{nom:<34} {taille_mo:>7.1f} Mo {pic_mo:>10.1f} Mo {ratio:>6.1f}x  {etat}")
        if not ok:
            break

    print("-" * 86)
    if ratios:
        r = max(ratios)
        print(f"\nRatio memoire / taille de fichier observe : jusqu'a {r:.1f}x")
        if dispo:
            limite = dispo / r
            print(f"Avec {dispo/1000:.1f} Go disponibles, la limite theorique est "
                  f"d'environ {limite:.0f} Mo par fichier.")
            print(f"Le cahier des charges annonce 200 Mo (UC-01), ce qui exigerait "
                  f"environ {200 * r / 1000:.1f} Go de memoire libre.")


if __name__ == "__main__":
    main()
