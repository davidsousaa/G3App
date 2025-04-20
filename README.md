# G3 App

## Setup

1. Copy `g3app` scenario to mosaic's scenarios folder

    $ `cp -r g3app <mosaic-path>/scenarios/g3app`

2. Build the application
    $ `mvn clean package`

3. Copy the `.jar` file to the scenario `application` directory
    $ `cp target/G3App-0.1.jar <mosaic-path>/scenarios/g3app/application/`

4. Run the simulation
    $ `./mosaic.sh -s g3app -w 0`

5. Logs for each app are located at `<mosaic-path>/logs/log-<date>-<time>-g3app/apps`

