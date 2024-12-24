local ReplicatedStorage = game:GetService('ReplicatedStorage')

local remote_img = require(game.ReplicatedStorage.remote_img)
local asset_service = game:GetService("AssetService");

local mojangApiUrl = "https://sessionserver.mojang.com/session/minecraft/profile/"


ReplicatedStorage:WaitForChild("loadPlayerSkin").OnClientEvent:Connect(function(uuid, player, Character)
	local skin = remote_img.create_image(mojangApiUrl .. uuid)
	for _, part in pairs(Character.SecondLayer:GetChildren()) do
		if part:IsA("MeshPart") then
			part.TextureContent = Content.fromObject(skin)
		end
	end

	for _, part in pairs(Character:GetChildren()) do
		if part:IsA("MeshPart") then
			part.TextureContent = Content.fromObject(skin)
		end
	end

end)