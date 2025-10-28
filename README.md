# 🧠 TP2 – Analyse statique de projets Java

Ce dépôt contient deux applications Java réalisées dans le cadre du **TP2 de Restructuration Logicielle**, ayant pour objectif de mesurer et visualiser le **couplage entre classes** dans un projet Java.  
Ces outils permettent d’identifier les **modules logiques** d’un logiciel à partir des relations d’appel entre classes.

---

## 📦 Contenu du dépôt

| Dossier | Description |
|----------|--------------|
| **TP2-PARTIE1** | Première version de l’analyse, basée sur **Eclipse JDT** pour extraire les informations du code source Java. |
| **TP2-PARTIE1-SPOON** | Version améliorée utilisant **Spoon** pour une extraction à plus haut niveau, avec détection automatique des modules et sélection dynamique du projet à analyser. |

---

## ⚙️ Prérequis

- **JDK 21**
- **Maven**
- Un projet Java à analyser (avec structure `src/main/java` ou équivalente)

---

## 🚀 Lancement de l’analyse

### 🔹 Version Eclipse JDT — `TP2-PARTIE1`

1. **Ouvrir le projet** dans un IDE Java (IntelliJ, Eclipse ou VS Code avec extension Java).
2. Ouvrir la classe principale :

   ```java
   // Fichier : src/main/java/org/example/Main.java

   public class Main {
       public static void main(String[] args) throws IOException {
           String src = ""; // chemin du dossier contenant les fichiers .java à analyser
           ...
       }
   }
   ```

3. Remplacer la ligne suivante :

   ```java
   String src = "";
   ```

   par le chemin absolu du dossier contenant les sources de ton projet Java.  
   Exemple :

   ```java
   String src = "C:/Users/Vincent/Bureau/targetASTMinify/src/main/java";
   ```

4. Lancer simplement la méthode `main()`.

   L’application exécutera automatiquement :
   - la découverte de toutes les classes du projet ;
   - le calcul du **couplage entre classes** ;
   - la construction du **graphe pondéré** et du **dendrogramme hiérarchique** ;
   - et l’ouverture d’une **interface Swing interactive** pour explorer les résultats.

---

### 🔹 Version Spoon — `TP2-PARTIE1-SPOON`

Cette version repose sur **Spoon**, un framework d’analyse statique plus haut niveau que JDT.  
Aucune configuration manuelle du chemin source n’est nécessaire.

1. **Exécuter la classe principale :**

   ```java
   // Fichier : src/main/java/hai913i/main/CodeGenerationProcessorMain.java
   public static void main(String[] args) { ... }
   ```

2. Au lancement, une **fenêtre de sélection de projet** s’affiche automatiquement :  
   il suffit de choisir le répertoire Java à analyser (ou de le saisir manuellement dans le champ prévu à cet effet).

3. Une fois le projet sélectionné, Spoon :
   - construit le **modèle sémantique (CtModel)** ;
   - exécute le **processeur d’analyse des classes** ;
   - calcule les **poids de couplage** et les **clusters** ;
   - et affiche les résultats dans la fenêtre Swing.

---

## 🧩 Interface utilisateur

L’interface Swing se compose de plusieurs onglets :

| Onglet | Description |
|--------|-------------|
| **Couplage** | Permet de calculer et d’afficher le couplage entre deux classes choisies. |
| **Graphe** | Visualisation du graphe pondéré des dépendances entre classes, avec seuil ajustable. |
| **Dendrogramme** | Représentation hiérarchique du regroupement des classes par similarité de couplage. |
| **Modules** *(Spoon uniquement)* | Détection automatique des modules selon un seuil de couplage interne \(CP\). |

> 💡 L’option « Niveaux uniformes » du dendrogramme permet d’aligner toutes les branches pour une lecture plus claire.  
> Le seuil \(CP\) permet d’identifier automatiquement les ensembles de classes formant un module cohérent.

---

## 🧪 Exemple d’utilisation (Eclipse JDT)

```java
public static void main(String[] args) throws IOException {
    String src = "C:/Users/Vincent/Bureau/targetASTMinify/src/main/java";
    ProjectParser parser = new ProjectParser(src);
    ...
}
```

Exécution → ouverture automatique de la fenêtre Swing :

- Onglet 1 : calcul du couplage entre deux classes ;
- Onglet 2 : graphe des dépendances pondérées ;
- Onglet 3 : dendrogramme hiérarchique des regroupements.

---

## 🧠 Concepts clés

- **Analyse statique** : extraction d’informations structurales sans exécuter le programme.
- **Couplage** : mesure du degré d’interdépendance entre classes d’un projet.
- **Graphe pondéré** : représentation des relations inter-classes par intensité.
- **Clustering hiérarchique** : regroupement itératif des classes les plus liées.
- **Dendrogramme** : visualisation arborescente des fusions successives de clusters.
- **Spoon** : framework Java de modélisation et transformation de code source.

---

## 🏁 Résumé

Les deux applications mettent en œuvre une approche complète d’analyse statique :
- Extraction des appels entre classes ;
- Calcul de couplage normalisé ;
- Visualisation sous forme de graphe et dendrogramme ;
- Détection automatique de modules logiques.

La version **JDT** offre un contrôle bas-niveau via les AST, tandis que la version **Spoon** apporte une approche plus robuste et généralisable, adaptée à l’analyse de projets réels.

---

## 🧾 Licence

Projet universitaire — Master Génie Logiciel, Université de Montpellier.  
Usage libre pour étude et expérimentation.
