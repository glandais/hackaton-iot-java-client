##Objectif

Tester le bon fonctionnement d'un [serveur](https://github.com/ltoinel/IoT-Development-Challenge).

Le client va envoyer des messages au serveur par lot. Entre chaque lot, si demandé, le client va vérifier des synthèses.

##Lancement

La classe `Main` sert de point d'entrée. Une seule commande est vraiment utile : `client`.
Celle ci dispose de pas mal de paramètres :

```
help client
```

```
NAME
        hackaton-client client - Client

SYNOPSIS
        hackaton-client [-host <host>] [-limites] [-maxValue <maxValue>]
                [-messagesLot <m>] [-nombreLots <n>] [-port <port>] [-sensors <sensors>]
                [-synthese] [-synthesesLot <syntheses>] [-threads <threads>] client

OPTIONS
        -host <host>
            Host (défaut : 192.168.1.1)

        -limites
            Tests de cas au limites (défaut : true)

        -maxValue <maxValue>
            value max (-1 pour toute la plage de long) (défaut : 10000)

        -messagesLot <m>
            Nombre de messages par lot (défaut : 1000)

        -nombreLots <n>
            Nombre de lots de messages (défaut : 100)

        -port <port>
            Port (défaut : 80)

        -sensors <sensors>
            Nombre de sensors (défaut : 10)

        -synthese
            Tests de la synthese (défaut : true)

        -synthesesLot <syntheses>
            Nombre de syntheses par lot (défaut : 10)

        -threads <threads>
            Nombre de threads (défaut : 10)
```

##Différences avec Gatling

Ici, on envoie des messages aléatoires avec un pool de n threads.
Le timestamp de chaque message n'est pas le timestamp système, on ajoute une variance aléatoire.
Une fois tous les messages d'un lot envoyé, on vérifie auprès du serveur que les valeurs sont bonnes, avec un début et une durée aléatoires.

Le stockage des valeurs se fait en mémoire dans une `TreeMap`, et la synthèse locale est obtenue avec `getSummary`.

Les valeurs sont vérifiées avec un objet Java BigDecimal. Un parser [JSR 353](https://jsonp.java.net/) est utilisé.

##Tests au limite

Les valeurs étant des long, on cherche à vérifier que les moyennes sont bien gérées si la somme sort du range de int64.

On teste aussi que pour plusieurs messages avec le même id, un seul est pris en compte.
Pour cela, on envoye de façon consécutive ou concurrente ces messages.
