<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHLoreValue

*Affiche dans le lore des items leur valeur en niveau d'île SuperiorSkyblock2 (côté client, via ProtocolLib).*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Affiche dans le lore des items leur valeur en niveau d'île SuperiorSkyblock2 (côté client, via ProtocolLib).

Utilisé sur : **Opale**.

## Dépendances

- Obligatoires : `ProtocolLib`

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shlorevalue reload` | — | Recharge `config.yml`, `messages.yml` et le fichier de valeurs de SuperiorSkyblock2 (console comprise). Si l'un est absent ou invalide, rien ne change et le message donne le fichier et l'erreur. Un inventaire déjà ouvert garde son ancien lore jusqu'à son prochain rafraîchissement. | `shlorevalue.admin.reload` |

`/shlorevalue` sans argument donne une ligne d'usage limitée aux sous-commandes autorisées.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shlorevalue.admin` | Toutes les sous-commandes d'administration (parent de `shlorevalue.admin.reload`). | `op` |
| `shlorevalue.admin.reload` | `/shlorevalue reload` | `op` |

## Configuration

Fichiers par défaut, avec des valeurs d'exemple. Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHLoreValue/
├── <a href="src/main/resources/config.yml">config.yml</a>     format du lore, chemin du fichier de valeurs de SuperiorSkyblock2
└── <a href="src/main/resources/messages.yml">messages.yml</a>   messages de la commande
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
