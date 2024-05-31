require 'optparse'

require_relative 'migrations'
require_relative 'models'

db_credentials = {}
server_port = {}
upload_dir = {}

options = {}
OptionParser.new do |opts|
  opts.banner = "Usage: main.rb [options]"

  opts.on("--credentials FILE", "Path to the credentials file") do |file|
    options[:credentials] = file
  end

  opts.on("--port PORT", Integer, "Port number to run the server") do |port|
    options[:port] = port
  end

  opts.on("--upload_dir DIRECTORY", "Path to the upload directory") do |dir|
    options[:upload_dir] = dir
  end
end.parse!

if options[:credentials]
  puts "Using credentials file: #{options[:credentials]}"

  def read_db_credentials(file_path)
    credentials = {}

    File.foreach(file_path) do |line|
      key, value = line.strip.split(": ")
      credentials[key.strip.to_sym] = value.strip if key && value
    end

    credentials
  end

  db_credentials = read_db_credentials(options[:credentials])
else
  puts "Please provide a credentials file using --credentials option."
end

if options[:port]
  puts "Running on port: #{options[:port]}"

  server_port = options[:port]
else
  puts "Please provide a port number using --port option."
end

if options[:upload_dir]
  puts "Upload directory: #{options[:upload_dir]}"

  upload_dir = options[:upload_dir]
else
  puts "Please provide an upload directory using --upload_dir option."
end

ActiveRecord::Base.establish_connection(
adapter: 'postgresql',
host: db_credentials[:host],
username: db_credentials[:username],
password: db_credentials[:password],
database: db_credentials[:database],
port: db_credentials[:port]
)

run_migrations

ARGV.clear

require 'sinatra'
require 'sinatra/json'
require 'rack/cors'
require 'fileutils'
require 'base64'
require 'puma'
require 'json'
require 'zip'
require 'rack'
require 'fastimage'

set :server, :puma
set :bind, '0.0.0.0'
set :port, server_port

FileUtils.mkdir_p(upload_dir)

configure do
  enable :cross_origin
end

use Rack::Cors do
  allow do
    origins '*'
    resource '*', headers: :any, methods: [:get, :post, :put, :delete, :options]
  end
end

options '*' do
  response.headers['Access-Control-Allow-Methods'] = 'GET, POST, PUT, DELETE, OPTIONS'
  response.headers['Access-Control-Allow-Headers'] =
    'Content-Type, Authorization, X-Requested-With, X-HTTP-Method-Override, Accept, Origin'
  response.headers['Access-Control-Allow-Origin'] = '*'
  200
end

get '/' do
  'Welcome to the API'
end

# Get all images
get '/images' do
  images = Image.all
  json images
end

# Get image by id
get '/images/:id' do
  image = Image.find_by(id: params[:id])

  if image
    json image
  else
    status 404
    json({ error: 'Image not found' })
  end
end

# Get image file by name
get '/image/:name' do
  image_name = params[:name]
  image_path = File.join(upload_dir, image_name)
  if File.exist?(image_path)
    send_file image_path
  else
    status 404
    json({ error: 'Image not found' })
  end
end

# Create image
post '/images' do
  data = JSON.parse(request.body.read)
  image = add_image(data['name'])

  if image
    status 201
    json image
  else
    status 422
    json({ error: 'Image could not be created' })
  end
end

# Delete image by id
delete '/image/:id' do
  image = Image.find(params[:id])
  if image
    image_path = File.join(upload_dir, image.name)
    File.delete(image_path) if File.exist?(image_path)
    image.destroy
    status 200
    json({ message: 'Image deleted successfully' })
  else
    status 404
    json({ error: 'Image not found' })
  end
end

post '/detections' do
  data = JSON.parse(request.body.read)

  detection = add_detection(
    data['image_name'], data['tag_name'],
    data['x_min'], data['y_min'],
    data['x_max'], data['y_max']
  )

  if detection
    status 201
    json detection
  else
    status 422
    json({ error: 'Detection could not be created' })
  end

end

get '/detections/:image_name' do
  image = Image.find_by(name: params[:image_name])
  if image
    detections = Detection.joins("INNER JOIN tags ON detections.tag_id = tags.id")
                          .select('detections.id, tags.name as tag_name, detections.x_min, detections.y_min, detections.x_max, detections.y_max')
                          .where(image_id: image.id)
    json detections
  else
    status 404
    json({ error: 'Image not found' })
  end
end

# API endpoint to update a detection using POST
post '/detection/:id' do
  detection = Detection.find(params[:id])
  data = JSON.parse(request.body.read)

  if detection
    tag = Tag.find_or_create_by(name: data["tag_name"])

    update_success = detection.update(
      tag_id: tag.id,
      x_min: data["x_min"],
      y_min: data["y_min"],
      x_max: data["x_max"],
      y_max: data["y_max"]
    )

    if update_success
      puts "Update successful for detection ID: #{params[:id]}"
      status 200
      json detection
    else
      puts "Update failed for detection ID: #{params[:id]}"
      status 400
      json({ error: 'Failed to update detection' })
    end
  else
    puts "Detection not found for ID: #{params[:id]}"
    status 404
    json({ error: 'Detection not found' })
  end
end

delete '/detection/:id' do
  detection = Detection.find(params[:id])

  if detection
    detection.destroy
    status 200
    json({ message: 'Detection deleted' })
  else
    status 404
    json({ error: 'Detection not found' })
  end
end

post '/upload_image' do
  if params[:file] && params[:file][:filename]
    filename = params[:file][:filename]
    file = params[:file][:tempfile]
    filepath = File.join(upload_dir, filename)

    File.open(filepath, 'wb') do |f|
      f.write(file.read)
    end

    status 201
    json({ message: 'File uploaded successfully', filename: filename, filepath: filepath })
  else
    status 400
    json({ error: 'No file uploaded' })
  end
end

# API endpoint to export images and their detections
get '/export' do
  content_type 'application/zip'
  attachment 'images_detections.zip'

  buffer = Zip::OutputStream.write_buffer do |zip|
    # Fetch all images and their detections
    images = Image.all
    tags = Tag.all

    # Add tags as a JSON file
    tags_data = tags.map do |tag|
      {
        id: tag.id,
        name: tag.name
      }
    end
    zip.put_next_entry("tags.json")
    zip.write JSON.pretty_generate(tags_data)

    images.each do |image|
      image_path = File.join(upload_dir, image.name)
      if File.exist?(image_path)
        # Add image file to the zip
        zip.put_next_entry("images/#{image.name}")
        zip.write File.read(image_path)

        # Get image dimensions
        dimensions = FastImage.size(image_path)
      end

      # Add detections as a JSON file
      detections = Detection.where(image_id: image.id)
      detections_data = detections.map do |detection|
        {
          id: detection.id,
          tag_id: detection.tag_id,
          x_min: detection.x_min,
          y_min: detection.y_min,
          x_max: detection.x_max,
          y_max: detection.y_max,
          width: dimensions[0],
          height: dimensions[1]
        }
      end

      json_filename = "#{File.basename(image.name, '.*')}.json"
      zip.put_next_entry("detections/#{json_filename}")
      zip.write JSON.pretty_generate(detections_data)
    end
  end

  buffer.rewind
  buffer.read
end

post '/upload' do
  if params[:file] && params[:file][:filename] && params[:detections]
    filename = params[:file][:filename]
    file = params[:file][:tempfile]
    filepath = File.join(upload_dir, filename)

    # Save the image file
    File.open(filepath, 'wb') do |f|
      f.write(file.read)
    end

    # Create the image record
    image = add_image(filename)

    # Parse and save detections
    detections_data = JSON.parse(params[:detections])
    detections_data.each do |detection|
      tag = Tag.find_or_create_by(name: detection['tag_name'])
      Detection.create(
        image: image,
        tag_id: tag.id,
        x_min: detection['x_min'],
        y_min: detection['y_min'],
        x_max: detection['x_max'],
        y_max: detection['y_max']
      )
    end

    status 201
    json({ message: 'Image and detections uploaded successfully' })
  else
    status 400
    json({ error: 'No file or detections provided' })
  end
end

not_found do
  status 404
  json({ error: 'Not found' })
end

error do
  status 500
  json({ error: 'Internal server error'})
end

Sinatra::Application.run!
