# G3 App

## Setup

1. Copy `tutorial` scenario to mosaic's scenarios folder

    $ `cp -r tutorial <mosaic-path>/scenarios/tutorial`

2. Build the application
    $ `mvn clean package`

3. Copy the `.jar` file to the scenario `application` directory
    $ `cp target/G3App-0.1.jar <mosaic-path>/scenarios/tutorial/application/`

4. Run the simulation
    $ `./mosaic.sh -s tutorial -w 0`

5. Logs for each app are located at `<mosaic-path>/logs/log-<date>-<time>-tutorial/apps`

